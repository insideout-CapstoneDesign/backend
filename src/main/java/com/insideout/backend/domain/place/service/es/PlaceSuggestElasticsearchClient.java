package com.insideout.backend.domain.place.service.es;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insideout.backend.domain.place.exception.PlaceErrorCode;
import com.insideout.backend.domain.place.exception.PlaceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
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

    public void upsertDocuments(List<SuggestDocument> documents) {
        try {
            upsertDocumentsStrict(documents);
        } catch (RuntimeException e) {
            // Best-effort background indexing: ignore failures.
            log.warn("[PlaceSuggestElasticsearchClient] Failed to upsert suggest documents.", e);
        }
    }

    public int upsertDocumentsStrict(List<SuggestDocument> documents) {
        String body = PlaceEsBulkUpsertBodyBuilder.build(documents, searchIndex, objectMapper);
        if (!StringUtils.hasText(body)) {
            return 0;
        }

        String primaryUri = resolvePrimaryUri(elasticsearchUris);
        RestClient restClient = restClientBuilder.baseUrl(primaryUri).build();

        try {
            String response = restClient.post()
                    .uri("/_bulk")
                    .contentType(MediaType.parseMediaType("application/x-ndjson"))
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parseBulkSuccessCount(response);
        } catch (RestClientException e) {
            throw new PlaceException(PlaceErrorCode.SEARCH_SERVICE_UNAVAILABLE);
        } catch (Exception e) {
            throw new PlaceException(PlaceErrorCode.SEARCH_SERVICE_UNAVAILABLE);
        }
    }

    private int parseBulkSuccessCount(String response) throws Exception {
        if (!StringUtils.hasText(response)) {
            throw new IllegalStateException("Empty bulk response");
        }

        Map<?, ?> parsed = objectMapper.readValue(response, Map.class);
        Object errors = parsed.get("errors");
        if (Boolean.TRUE.equals(errors)) {
            throw new IllegalStateException("Bulk indexing returned errors");
        }

        Object items = parsed.get("items");
        if (!(items instanceof List<?> itemList)) {
            throw new IllegalStateException("Bulk indexing response does not contain items");
        }

        int successCount = 0;
        for (Object item : itemList) {
            if (!(item instanceof Map<?, ?> action)) {
                continue;
            }
            Object payload = action.values().stream().findFirst().orElse(null);
            if (!(payload instanceof Map<?, ?> result)) {
                continue;
            }
            Object status = result.get("status");
            if (status instanceof Number statusNumber && statusNumber.intValue() >= 200 && statusNumber.intValue() < 300) {
                successCount++;
                continue;
            }
            throw new IllegalStateException("Bulk indexing item failed");
        }

        return successCount;
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
                .body(PlaceEsSearchRequestBuilder.build(query, size, lat, lng, radius, includeSuggester))
                .retrieve()
                .body(String.class);
        return parseResponse(response);
    }

    private SearchResponse parseResponse(String response) throws Exception {
        PlaceEsSearchResponseParser.SearchResponse parsed = PlaceEsSearchResponseParser.parse(response, objectMapper);
        return new SearchResponse(parsed.documents(), parsed.correctedQuery());
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
