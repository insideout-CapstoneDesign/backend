package com.insideout.backend.domain.place.service.search;

import com.insideout.backend.domain.place.dto.response.PlaceSearchItemResponse;
import com.insideout.backend.domain.place.exception.PlaceErrorCode;
import com.insideout.backend.domain.place.exception.PlaceException;
import com.insideout.backend.domain.place.service.es.PlaceSuggestElasticsearchClient;
import com.insideout.backend.domain.place.service.kakao.KakaoPlaceSearchClient;
import com.insideout.backend.domain.place.service.support.PlaceSearchSupport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class PlaceSearchExternalPipeline {

    private PlaceSearchExternalPipeline() {
    }

    static List<PlaceSearchItemResponse> fetchExternalPlaces(
            String normalizedQuery,
            Double lat,
            Double lng,
            Integer resolvedSearchRadius,
            boolean hasCoordinate,
            int resolvedSize,
            PlaceSuggestElasticsearchClient placeSuggestElasticsearchClient,
            KakaoPlaceSearchClient kakaoPlaceSearchClient,
            PlaceSearchIndexingService placeSearchIndexingService
    ) {
        List<PlaceSearchItemResponse> fromElasticsearch;
        try {
            fromElasticsearch = placeSuggestElasticsearchClient.search(
                            normalizedQuery,
                            resolvedSize,
                            lat,
                            lng,
                            resolvedSearchRadius
                    ).stream()
                    .map(PlaceSearchExternalPipeline::toExternalSearchItem)
                    .toList();
        } catch (PlaceException e) {
            if (e.getErrorCode() != PlaceErrorCode.SEARCH_SERVICE_UNAVAILABLE) {
                throw e;
            }
            fromElasticsearch = List.of();
        }

        List<PlaceSearchItemResponse> fromKakao = hasCoordinate
                ? kakaoPlaceSearchClient.searchByKeyword(normalizedQuery, lat, lng, resolvedSearchRadius)
                : kakaoPlaceSearchClient.searchByKeyword(normalizedQuery);

        if (fromElasticsearch.isEmpty()) {
            placeSearchIndexingService.upsertFromSearchResultsAsync(fromKakao.stream().limit(resolvedSize * 2L).toList());
            return fromKakao;
        }

        if (fromElasticsearch.size() >= resolvedSize) {
            return fromElasticsearch;
        }

        Map<String, PlaceSearchItemResponse> combined = new LinkedHashMap<>();
        for (PlaceSearchItemResponse item : fromElasticsearch) {
            combined.put(PlaceSearchSupport.dedupeKey(item), item);
        }
        for (PlaceSearchItemResponse item : fromKakao) {
            combined.putIfAbsent(PlaceSearchSupport.dedupeKey(item), item);
            if (combined.size() >= resolvedSize * 2) {
                break;
            }
        }

        Set<String> esKeys = fromElasticsearch.stream()
                .map(PlaceSearchSupport::dedupeKey)
                .collect(Collectors.toSet());
        List<PlaceSearchItemResponse> onlyFromKakao = fromKakao.stream()
                .filter(item -> !esKeys.contains(PlaceSearchSupport.dedupeKey(item)))
                .limit(resolvedSize)
                .toList();
        placeSearchIndexingService.upsertFromSearchResultsAsync(onlyFromKakao);
        return new ArrayList<>(combined.values());
    }

    private static PlaceSearchItemResponse toExternalSearchItem(PlaceSuggestElasticsearchClient.SuggestDocument document) {
        return new PlaceSearchItemResponse(
                document.name(),
                document.address(),
                document.roadAddress(),
                document.lat(),
                document.lng(),
                false,
                document.externalApiId(),
                null
        );
    }
}
