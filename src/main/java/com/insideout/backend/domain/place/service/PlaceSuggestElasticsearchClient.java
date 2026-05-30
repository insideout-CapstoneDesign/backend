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
        String primaryUri = resolvePrimaryUri(elasticsearchUris);
        RestClient restClient = restClientBuilder.baseUrl(primaryUri).build();

        try {
            String response = restClient.post()
                    .uri("/{index}/_search", searchIndex)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildRequestBody(query, size, lat, lng, null))
                    .retrieve()
                    .body(String.class);
            return parseResponse(response);
        } catch (RestClientException e) {
            throw new PlaceException(PlaceErrorCode.SEARCH_SERVICE_UNAVAILABLE);
        } catch (Exception e) {
            throw new PlaceException(PlaceErrorCode.SEARCH_SERVICE_UNAVAILABLE);
        }
    }

    private String resolvePrimaryUri(String uris) {
        if (!StringUtils.hasText(uris)) {
            return "http://localhost:9200";
        }
        String[] split = uris.split(",");
        return split[0].trim();
    }

    public List<SuggestDocument> search(String query, int size, Double lat, Double lng, Integer radius) {
        String primaryUri = resolvePrimaryUri(elasticsearchUris);
        RestClient restClient = restClientBuilder.baseUrl(primaryUri).build();

        try {
            String response = restClient.post()
                    .uri("/{index}/_search", searchIndex)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildRequestBody(query, size, lat, lng, radius))
                    .retrieve()
                    .body(String.class);
            return parseResponse(response);
        } catch (RestClientException e) {
            throw new PlaceException(PlaceErrorCode.SEARCH_SERVICE_UNAVAILABLE);
        } catch (Exception e) {
            throw new PlaceException(PlaceErrorCode.SEARCH_SERVICE_UNAVAILABLE);
        }
    }

    private Map<String, Object> buildRequestBody(String query, int size, Double lat, Double lng, Integer radius) {
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
        return requestBody;
    }

    private List<SuggestDocument> parseResponse(String response) throws Exception {
        if (!StringUtils.hasText(response)) {
            return List.of();
        }

        JsonNode root = objectMapper.readTree(response);
        JsonNode hits = root.path("hits").path("hits");
        if (!hits.isArray()) {
            return List.of();
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
        return results;
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
}
