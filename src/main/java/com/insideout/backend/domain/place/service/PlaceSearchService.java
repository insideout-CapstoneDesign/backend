package com.insideout.backend.domain.place.service;

import com.insideout.backend.domain.place.dto.response.PlaceNearestResponse;
import com.insideout.backend.domain.place.dto.response.PlaceSearchItemResponse;
import com.insideout.backend.domain.place.exception.PlaceErrorCode;
import com.insideout.backend.domain.place.exception.PlaceException;
import com.insideout.backend.global.apiPayload.code.GeneralErrorCode;
import com.insideout.backend.global.apiPayload.exception.ProjectException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PlaceSearchService {

    private static final int DEFAULT_RADIUS_METER = 30;
    private static final int DEFAULT_SEARCH_RADIUS_METER = 5_000;
    private static final int MIN_RADIUS_METER = 1;
    private static final int MAX_RADIUS_METER = 20_000;

    private final KakaoPlaceSearchClient kakaoPlaceSearchClient;

    public List<PlaceSearchItemResponse> search(String query, Double lat, Double lng, Integer radius) {
        if (query == null || query.isBlank()) {
            throw new ProjectException(GeneralErrorCode.BAD_REQUEST);
        }
        if (lat == null && lng == null) {
            return kakaoPlaceSearchClient.searchByKeyword(query.trim());
        }

        validateCoordinate(lat, lng);
        int resolvedRadius = normalizeSearchRadius(radius);
        return kakaoPlaceSearchClient.searchByKeyword(query.trim(), lat, lng, resolvedRadius).stream()
                .filter(item -> item.lat() != null && item.lng() != null)
                .map(item -> new ScoredPlace(item, distanceInMeter(lat, lng, item.lat(), item.lng())))
                .filter(scored -> scored.distanceMeter() <= resolvedRadius)
                .sorted(Comparator.comparingDouble(ScoredPlace::distanceMeter))
                .map(ScoredPlace::item)
                .toList();
    }

    public Optional<PlaceNearestResponse> findNearest(Double lat, Double lng, Integer radius) {
        validateCoordinate(lat, lng);
        int resolvedRadius = normalizeRadius(radius);
        return kakaoPlaceSearchClient.findNearestByCoordinate(lat, lng, resolvedRadius);
    }

    private void validateCoordinate(Double lat, Double lng) {
        if (lat == null || lng == null || !Double.isFinite(lat) || !Double.isFinite(lng)) {
            throw new PlaceException(PlaceErrorCode.INVALID_COORDINATE);
        }

        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new PlaceException(PlaceErrorCode.INVALID_COORDINATE);
        }
    }

    private int normalizeRadius(Integer radius) {
        if (radius == null) {
            return DEFAULT_RADIUS_METER;
        }

        if (radius < MIN_RADIUS_METER || radius > MAX_RADIUS_METER) {
            throw new PlaceException(PlaceErrorCode.INVALID_RADIUS);
        }

        return radius;
    }

    private int normalizeSearchRadius(Integer radius) {
        if (radius == null) {
            return DEFAULT_SEARCH_RADIUS_METER;
        }

        if (radius < MIN_RADIUS_METER || radius > MAX_RADIUS_METER) {
            throw new PlaceException(PlaceErrorCode.INVALID_RADIUS);
        }

        return radius;
    }

    private double distanceInMeter(double lat1, double lng1, double lat2, double lng2) {
        double earthRadius = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }

    private record ScoredPlace(PlaceSearchItemResponse item, double distanceMeter) {
    }
}
