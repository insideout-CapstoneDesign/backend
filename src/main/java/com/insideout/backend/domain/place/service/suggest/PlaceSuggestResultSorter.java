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
                .comparingInt((PlaceSearchItemResponse item) -> keywordScore(item.name(), query)).reversed();

        if (lat != null && lng != null) {
            comparator = comparator.thenComparing(
                    PlaceSearchItemResponse::distanceMeters,
                    Comparator.nullsLast(Double::compareTo)
            );
        }

        return comparator
                .thenComparing(PlaceSearchItemResponse::isRegistered, Comparator.reverseOrder())
                .thenComparing(item -> PlaceSearchSupport.normalizeText(item.name()));
    }

    private static int keywordScore(String name, String query) {
        String target = PlaceSearchSupport.normalizeText(name);
        String keyword = PlaceSearchSupport.normalizeText(query);
        if (!StringUtils.hasText(target) || !StringUtils.hasText(keyword)) {
            return 0;
        }
        if (target.equals(keyword)) {
            return 3;
        }
        if (target.startsWith(keyword)) {
            return 2;
        }
        if (target.contains(keyword)) {
            return 1;
        }
        return 0;
    }
}

