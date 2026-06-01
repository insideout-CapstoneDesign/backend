package com.insideout.backend.domain.place.service.suggest;

final class PlaceSuggestFallbackPolicy {

    private static final int MIN_NEARBY_FALLBACK_SIZE = 10;

    private PlaceSuggestFallbackPolicy() {
    }

    static int resolveFallbackSize(int resolvedSize, int esResultSize, Double lat, Double lng) {
        int needed = Math.max(0, resolvedSize - esResultSize);
        if (lat == null || lng == null) {
            return needed;
        }
        int nearbyFallback = Math.min(resolvedSize, MIN_NEARBY_FALLBACK_SIZE);
        return Math.max(needed, nearbyFallback);
    }
}

