package com.insideout.backend.domain.place.service;

import com.insideout.backend.domain.place.dto.response.PlaceNearestResponse;
import com.insideout.backend.domain.place.dto.response.PlaceSearchItemResponse;
import com.insideout.backend.domain.place.exception.PlaceErrorCode;
import com.insideout.backend.domain.place.exception.PlaceException;
import com.insideout.backend.global.apiPayload.code.GeneralErrorCode;
import com.insideout.backend.global.apiPayload.exception.ProjectException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PlaceSearchService {

    private static final int DEFAULT_RADIUS_METER = 30;
    private static final int MIN_RADIUS_METER = 1;
    private static final int MAX_RADIUS_METER = 20_000;

    private final KakaoPlaceSearchClient kakaoPlaceSearchClient;

    public List<PlaceSearchItemResponse> search(String query) {
        if (query == null || query.isBlank()) {
            throw new ProjectException(GeneralErrorCode.BAD_REQUEST);
        }
        return kakaoPlaceSearchClient.searchByKeyword(query.trim());
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
}
