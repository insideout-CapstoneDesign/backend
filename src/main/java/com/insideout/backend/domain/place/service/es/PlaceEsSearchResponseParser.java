package com.insideout.backend.domain.place.service.es;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

final class PlaceEsSearchResponseParser {

    private PlaceEsSearchResponseParser() {
    }

    static SearchResponse parse(String response, ObjectMapper objectMapper) throws Exception {
        if (!StringUtils.hasText(response)) {
            return new SearchResponse(List.of(), null);
        }

        JsonNode root = objectMapper.readTree(response);
        JsonNode hits = root.path("hits").path("hits");
        if (!hits.isArray()) {
            return new SearchResponse(List.of(), extractCorrectedQuery(root));
        }

        List<PlaceSuggestElasticsearchClient.SuggestDocument> results = new ArrayList<>();
        for (JsonNode hit : hits) {
            JsonNode source = hit.path("_source");
            if (source.isMissingNode()) {
                continue;
            }
            JsonNode location = source.path("location");
            Double lat = nullableDouble(location.path("lat"));
            Double lng = nullableDouble(location.path("lon"));
            results.add(new PlaceSuggestElasticsearchClient.SuggestDocument(
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

    private static String extractCorrectedQuery(JsonNode root) {
        JsonNode options = root.path("suggest").path("name_suggest");
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

    private static String nullableText(JsonNode node) {
        return node != null && !node.isMissingNode() && !node.isNull() ? node.asText() : null;
    }

    private static Double nullableDouble(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.asDouble();
    }

    record SearchResponse(
            List<PlaceSuggestElasticsearchClient.SuggestDocument> documents,
            String correctedQuery
    ) {
    }
}

