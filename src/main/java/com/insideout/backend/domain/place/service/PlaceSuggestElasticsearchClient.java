package com.insideout.backend.domain.place.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insideout.backend.domain.place.exception.PlaceErrorCode;
import com.insideout.backend.domain.place.exception.PlaceException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PlaceSuggestElasticsearchClient {

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${spring.elasticsearch.uris:http://localhost:9200}")
    private String elasticsearchUris;

    @Value("${place.search.index:places_v1}")
    private String searchIndex;

    public List<SuggestDocument> suggest(String query, int size, Double lat, Double lng) {
        return doSearch(query, size, lat, lng, null);
    }

    private String resolvePrimaryUri(String uris) {
        if (!StringUtils.hasText(uris)) {
            return "http://localhost:9200";
        }
        String[] split = uris.split(",");
        return split[0].trim();
    }

    public List<SuggestDocument> search(String query, int size, Double lat, Double lng, Integer radius) {
        return doSearch(query, size, lat, lng, radius);
    }

    private List<SuggestDocument> doSearch(String query, int size, Double lat, Double lng, Integer radius) {
        String primaryUri = resolvePrimaryUri(elasticsearchUris);
        RestClient restClient = restClientBuilder.baseUrl(primaryUri).build();

        try {
            SearchResponse first = executeSearch(restClient, query, size, lat, lng, radius, true);
            if (!first.documents().isEmpty()) {
                return first.documents();
            }
            if (!StringUtils.hasText(first.correctedQuery()) || query.equals(first.correctedQuery())) {
                return first.documents();
            }
            SearchResponse retried = executeSearch(restClient, first.correctedQuery(), size, lat, lng, radius, false);
            return retried.documents();
        } catch (RestClientException e) {
            throw new PlaceException(PlaceErrorCode.SEARCH_SERVICE_UNAVAILABLE);
        } catch (Exception e) {
            throw new PlaceException(PlaceErrorCode.SEARCH_SERVICE_UNAVAILABLE);
        }
    }

    private SearchResponse executeSearch(
            RestClient restClient,
            String query,
            int size,
            Double lat,
            Double lng,
            Integer radius,
            boolean includeSuggester
    ) throws Exception {
        String response = restClient.post()
                .uri("/{index}/_search", searchIndex)
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildRequestBody(query, size, lat, lng, radius, includeSuggester))
                .retrieve()
                .body(String.class);
        return parseResponse(response);
    }

    private Map<String, Object> buildRequestBody(
            String query,
            int size,
            Double lat,
            Double lng,
            Integer radius,
            boolean includeSuggester
    ) {
        Map<String, Object> termNameKeyword = Map.of(
                "term", Map.of(
                        "name.keyword", Map.of(
                                "value", query,
                                "boost", 20
                        )
                )
        );
        Map<String, Object> matchPhraseName = Map.of(
                "match_phrase", Map.of(
                        "name", Map.of(
                                "query", query,
                                "boost", 10
                        )
                )
        );
        Map<String, Object> matchName = Map.of(
                "match", Map.of(
                        "name", Map.of(
                                "query", query,
                                "operator", "and",
                                "boost", 5
                        )
                )
        );
        Map<String, Object> fuzzyName = Map.of(
                "match", Map.of(
                        "name", Map.of(
                                "query", query,
                                "fuzziness", "AUTO",
                                "boost", 2
                        )
                )
        );
        Map<String, Object> matchAddress = Map.of(
                "match", Map.of(
                        "address", Map.of(
                                "query", query,
                                "boost", 1
                        )
                )
        );
        Map<String, Object> matchRoadAddress = Map.of(
                "match", Map.of(
                        "roadAddress", Map.of(
                                "query", query,
                                "boost", 1
                        )
                )
        );

        List<Object> should = List.of(
                termNameKeyword,
                matchPhraseName,
                matchName,
                fuzzyName,
                matchAddress,
                matchRoadAddress
        );

        Map<String, Object> bool = new LinkedHashMap<>();
        bool.put("should", should);
        bool.put("minimum_should_match", 1);
        if (lat != null && lng != null && radius != null) {
            bool.put("filter", List.of(
                    Map.of("geo_distance", Map.of(
                            "distance", radius + "m",
                            "location", Map.of("lat", lat, "lon", lng)
                    ))
            ));
        }

        List<Object> sort = new ArrayList<>();
        sort.add(Map.of("_score", Map.of("order", "desc")));
        if (lat != null && lng != null) {
            sort.add(Map.of(
                    "_geo_distance", Map.of(
                            "location", Map.of("lat", lat, "lon", lng),
                            "order", "asc",
                            "unit", "m"
                    )
            ));
        }

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("size", size);
        requestBody.put("_source", List.of("name", "address", "roadAddress", "externalApiId", "location"));
        requestBody.put("query", Map.of("bool", bool));
        requestBody.put("sort", sort);
        if (includeSuggester) {
            requestBody.put("suggest", Map.of(
                    "name_suggest", Map.of(
                            "text", query,
                            "term", Map.of(
                                    "field", "name",
                                    "suggest_mode", "popular",
                                    "max_edits", 2
                            )
                    )
            ));
        }
        return requestBody;
    }

    private SearchResponse parseResponse(String response) throws Exception {
        if (!StringUtils.hasText(response)) {
            return new SearchResponse(List.of(), null);
        }

        JsonNode root = objectMapper.readTree(response);
        JsonNode hits = root.path("hits").path("hits");
        if (!hits.isArray()) {
            return new SearchResponse(List.of(), extractCorrectedQuery(root));
        }

        List<SuggestDocument> results = new ArrayList<>();
        for (JsonNode hit : hits) {
            JsonNode source = hit.path("_source");
            if (source.isMissingNode()) {
                continue;
            }
            JsonNode location = source.path("location");
            Double lat = nullableDouble(location.path("lat"));
            Double lng = nullableDouble(location.path("lon"));
            results.add(new SuggestDocument(
                    nullableText(source.path("name")),
                    nullableText(source.path("address")),
                    nullableText(source.path("roadAddress")),
                    nullableText(source.path("externalApiId")),
                    lat,
                    lng
            ));
        }
        return new SearchResponse(results, extractCorrectedQuery(root));
    }

    private String extractCorrectedQuery(JsonNode root) {
        JsonNode options = root.path("suggest")
                .path("name_suggest");
        if (!options.isArray() || options.isEmpty()) {
            return null;
        }
        JsonNode firstSuggest = options.get(0);
        JsonNode suggestOptions = firstSuggest.path("options");
        if (!suggestOptions.isArray() || suggestOptions.isEmpty()) {
            return null;
        }
        JsonNode firstOption = suggestOptions.get(0);
        String corrected = nullableText(firstOption.path("text"));
        return StringUtils.hasText(corrected) ? corrected.trim() : null;
    }

    private String nullableText(JsonNode node) {
        return node != null && !node.isMissingNode() && !node.isNull() ? node.asText() : null;
    }

    private Double nullableDouble(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.asDouble();
    }

    public record SuggestDocument(
            String name,
            String address,
            String roadAddress,
            String externalApiId,
            Double lat,
            Double lng
    ) {
    }

    private record SearchResponse(
            List<SuggestDocument> documents,
            String correctedQuery
    ) {
    }
}
