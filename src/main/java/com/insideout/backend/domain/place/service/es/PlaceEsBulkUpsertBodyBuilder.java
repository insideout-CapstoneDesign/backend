package com.insideout.backend.domain.place.service.es;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insideout.backend.domain.place.service.support.PlaceSearchSupport;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class PlaceEsBulkUpsertBodyBuilder {

    private PlaceEsBulkUpsertBodyBuilder() {
    }

    static String build(List<PlaceSuggestElasticsearchClient.SuggestDocument> documents, String indexName, ObjectMapper objectMapper) {
        if (documents == null || documents.isEmpty()) {
            return "";
        }

        return documents.stream()
                .filter(PlaceEsBulkUpsertBodyBuilder::isIndexable)
                .map(document -> toBulkUpdateLine(document, indexName, objectMapper))
                .collect(Collectors.joining());
    }

    private static boolean isIndexable(PlaceSuggestElasticsearchClient.SuggestDocument document) {
        return StringUtils.hasText(document.name())
                && document.lat() != null
                && document.lng() != null;
    }

    private static String toBulkUpdateLine(
            PlaceSuggestElasticsearchClient.SuggestDocument document,
            String indexName,
            ObjectMapper objectMapper
    ) {
        String docId;
        if (StringUtils.hasText(document.externalApiId())) {
            docId = "ext-" + document.externalApiId().trim();
        } else {
            docId = "name-" + PlaceSearchSupport.normalizeText(document.name())
                    + "-" + PlaceSearchSupport.round(document.lat(), 4)
                    + "-" + PlaceSearchSupport.round(document.lng(), 4);
        }

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("name", document.name());
        doc.put("address", document.address());
        doc.put("roadAddress", document.roadAddress());
        doc.put("externalApiId", document.externalApiId());
        doc.put("location", Map.of("lat", document.lat(), "lon", document.lng()));

        Map<String, Object> root = Map.of(
                "doc", doc,
                "doc_as_upsert", true
        );

        try {
            String action = objectMapper.writeValueAsString(Map.of("update", Map.of("_index", indexName, "_id", docId)));
            String payload = objectMapper.writeValueAsString(root);
            return action + "\n" + payload + "\n";
        } catch (Exception e) {
            return "";
        }
    }
}
