package com.insideout.backend.domain.place.service.support;

import com.insideout.backend.domain.place.dto.response.PlaceSearchItemResponse;
import com.insideout.backend.domain.place.exception.PlaceErrorCode;
import com.insideout.backend.domain.place.exception.PlaceException;
import com.insideout.backend.domain.place.service.es.PlaceSuggestElasticsearchClient;
import org.springframework.util.StringUtils;

import java.util.Locale;

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
        double normalizedA = Math.max(0.0, Math.min(1.0, a));
        double c = 2 * Math.atan2(Math.sqrt(normalizedA), Math.sqrt(1 - normalizedA));
        return earthRadius * c;
    }

    public static String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
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
}
