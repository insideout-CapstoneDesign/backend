package com.insideout.backend.domain.place.service.support;

import com.insideout.backend.domain.place.dto.response.PlaceSearchItemResponse;
import com.insideout.backend.domain.place.exception.PlaceErrorCode;
import com.insideout.backend.domain.place.exception.PlaceException;
import com.insideout.backend.domain.place.service.es.PlaceSuggestElasticsearchClient;
import org.springframework.util.StringUtils;

public final class PlaceSearchSupport {

    private PlaceSearchSupport() {
    }

    public static void validateCoordinate(Double lat, Double lng, boolean allowBothNull) {
        if (allowBothNull && lat == null && lng == null) {
            return;
        }
        if (lat == null || lng == null || !Double.isFinite(lat) || !Double.isFinite(lng)) {
            throw new PlaceException(PlaceErrorCode.INVALID_COORDINATE);
        }
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new PlaceException(PlaceErrorCode.INVALID_COORDINATE);
        }
    }

    public static double distanceInMeter(double lat1, double lng1, double lat2, double lng2) {
        double earthRadius = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }

    public static String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replaceAll("\\s+", "").toLowerCase();
    }

    public static String searchableText(PlaceSearchItemResponse item) {
        if (item == null) {
            return "";
        }

        if (StringUtils.hasText(item.parentBuildingName()) && StringUtils.hasText(item.name())) {
            return item.parentBuildingName().trim() + item.name().trim();
        }

        if (StringUtils.hasText(item.displayName())) {
            return item.displayName();
        }

        return item.name();
    }

    public static String displayLabel(PlaceSearchItemResponse item) {
        if (item == null) {
            return "";
        }
        if (StringUtils.hasText(item.displayName())) {
            return item.displayName();
        }
        return item.name();
    }

    public static int canonicalKeywordScore(PlaceSearchItemResponse item, String query) {
        if (item == null) {
            return 0;
        }
        return keywordScore(item.name(), query);
    }

    public static int displayKeywordScore(PlaceSearchItemResponse item, String query) {
        if (item == null) {
            return 0;
        }
        return keywordScore(searchableText(item), query);
    }

    public static double round(double value, int precision) {
        double scale = Math.pow(10, precision);
        return Math.round(value * scale) / scale;
    }

    public static String dedupeKey(PlaceSearchItemResponse item) {
        if (StringUtils.hasText(item.externalApiId())) {
            return "ext:" + item.externalApiId().trim();
        }
        if (item.lat() != null && item.lng() != null) {
            return "geo:" + normalizeText(item.name()) + ":" + round(item.lat(), 4) + ":" + round(item.lng(), 4);
        }
        return "name:" + normalizeText(item.name()) + "|" + normalizeText(item.address());
    }

    public static String dedupeKey(PlaceSuggestElasticsearchClient.SuggestDocument item) {
        if (StringUtils.hasText(item.externalApiId())) {
            return "ext:" + item.externalApiId().trim();
        }
        if (item.lat() != null && item.lng() != null) {
            return "geo:" + normalizeText(item.name()) + ":" + round(item.lat(), 4) + ":" + round(item.lng(), 4);
        }
        return "name:" + normalizeText(item.name()) + "|" + normalizeText(item.address());
    }

    public static int keywordScore(String name, String query) {
        String target = normalizeText(name);
        String keyword = normalizeText(query);
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
