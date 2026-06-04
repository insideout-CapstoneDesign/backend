package com.insideout.backend.domain.place.service.suggest;

import com.insideout.backend.domain.place.dto.response.PlaceSearchItemResponse;
import com.insideout.backend.domain.place.service.support.PlaceSearchSupport;
import org.springframework.util.StringUtils;

import java.util.Comparator;

final class PlaceSuggestResultSorter {

    private PlaceSuggestResultSorter() {
    }

    static Comparator<PlaceSearchItemResponse> comparator(String query, Double lat, Double lng) {
        Comparator<PlaceSearchItemResponse> comparator = Comparator
                .comparingInt((PlaceSearchItemResponse item) -> PlaceSearchSupport.keywordScore(item, query)).reversed();

        if (lat != null && lng != null) {
            comparator = comparator.thenComparing(
                    PlaceSearchItemResponse::distanceMeters,
                    Comparator.nullsLast(Double::compareTo)
            );
        }

        return comparator
                .thenComparing(PlaceSearchItemResponse::isRegistered, Comparator.reverseOrder())
                .thenComparing(item -> PlaceSearchSupport.normalizeText(PlaceSearchSupport.displayLabel(item)));
    }
}
