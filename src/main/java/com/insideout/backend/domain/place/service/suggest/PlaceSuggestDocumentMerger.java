package com.insideout.backend.domain.place.service.suggest;

import com.insideout.backend.domain.place.dto.response.PlaceSearchItemResponse;
import com.insideout.backend.domain.place.service.es.PlaceSuggestElasticsearchClient;
import com.insideout.backend.domain.place.service.support.PlaceSearchSupport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PlaceSuggestDocumentMerger {

    private PlaceSuggestDocumentMerger() {
    }

    static List<PlaceSuggestElasticsearchClient.SuggestDocument> merge(
            List<PlaceSuggestElasticsearchClient.SuggestDocument> fromElasticsearch,
            List<PlaceSearchItemResponse> fromKakao,
            int size
    ) {
        Map<String, PlaceSuggestElasticsearchClient.SuggestDocument> merged = new LinkedHashMap<>();
        for (PlaceSuggestElasticsearchClient.SuggestDocument item : fromElasticsearch) {
            merged.put(PlaceSearchSupport.dedupeKey(item), item);
        }
        for (PlaceSearchItemResponse item : fromKakao) {
            PlaceSuggestElasticsearchClient.SuggestDocument document = new PlaceSuggestElasticsearchClient.SuggestDocument(
                    item.name(),
                    item.address(),
                    item.roadAddress(),
                    item.externalApiId(),
                    item.lat(),
                    item.lng()
            );
            merged.putIfAbsent(PlaceSearchSupport.dedupeKey(document), document);
            if (merged.size() >= size * 2) {
                break;
            }
        }
        return merged.values().stream().limit(size * 2L).toList();
    }
}

