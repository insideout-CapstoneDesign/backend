package com.insideout.backend.domain.place.service;

import com.insideout.backend.domain.building.repository.BuildingRepository;
import com.insideout.backend.domain.building.repository.BuildingSearchProjection;
import com.insideout.backend.domain.place.dto.response.PlaceSearchItemResponse;
import com.insideout.backend.domain.place.exception.PlaceErrorCode;
import com.insideout.backend.domain.place.exception.PlaceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlaceSuggestService {

    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 50;
    private static final int MIN_QUERY_LENGTH = 2;

    private final PlaceSuggestElasticsearchClient placeSuggestElasticsearchClient;
    private final KakaoPlaceSearchClient kakaoPlaceSearchClient;
    private final PlaceSearchIndexingService placeSearchIndexingService;
    private final BuildingRepository buildingRepository;

    public List<PlaceSearchItemResponse> suggest(String query, Double lat, Double lng, Integer size) {
        String normalizedQuery = validateAndNormalizeQuery(query);
        validateCoordinate(lat, lng);
        int resolvedSize = normalizeSize(size);

        List<PlaceSuggestElasticsearchClient.SuggestDocument> fromElasticsearch = placeSuggestElasticsearchClient
                .suggest(normalizedQuery, resolvedSize, lat, lng);

        List<PlaceSearchItemResponse> fromKakao = fetchKakaoFallback(normalizedQuery, lat, lng);
        List<PlaceSuggestElasticsearchClient.SuggestDocument> mergedSuggested = mergeSuggested(fromElasticsearch, fromKakao, resolvedSize);

        if (fromElasticsearch.size() < resolvedSize && !fromKakao.isEmpty()) {
            placeSearchIndexingService.upsertFromSearchResultsAsync(fromKakao.stream().limit(resolvedSize).toList());
        }

        if (mergedSuggested.isEmpty()) {
            return List.of();
        }

        Map<String, BuildingSearchProjection> registeredByExternalApiId = resolveRegisteredMap(mergedSuggested);

        return mergedSuggested.stream()
                .map(item -> toResponse(item, registeredByExternalApiId, lat, lng))
                .limit(resolvedSize)
                .toList();
    }

    private String validateAndNormalizeQuery(String query) {
        if (!StringUtils.hasText(query)) {
            throw new PlaceException(PlaceErrorCode.SEARCH_INVALID_QUERY);
        }
        String normalized = query.trim();
        if (normalized.length() < MIN_QUERY_LENGTH) {
            throw new PlaceException(PlaceErrorCode.SEARCH_INVALID_QUERY);
        }
        return normalized;
    }

    private void validateCoordinate(Double lat, Double lng) {
        if (lat == null && lng == null) {
            return;
        }

        if (lat == null || lng == null || !Double.isFinite(lat) || !Double.isFinite(lng)) {
            throw new PlaceException(PlaceErrorCode.INVALID_COORDINATE);
        }

        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new PlaceException(PlaceErrorCode.INVALID_COORDINATE);
        }
    }

    private int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }

        if (size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private Map<String, BuildingSearchProjection> resolveRegisteredMap(
            List<PlaceSuggestElasticsearchClient.SuggestDocument> suggested
    ) {
        Set<String> externalApiIds = suggested.stream()
                .map(PlaceSuggestElasticsearchClient.SuggestDocument::externalApiId)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toSet());

        if (externalApiIds.isEmpty()) {
            return Map.of();
        }

        return buildingRepository.findRegisteredPlacesByExternalApiIds(externalApiIds).stream()
                .filter(item -> StringUtils.hasText(item.getExternalApiId()))
                .collect(Collectors.toMap(
                        item -> item.getExternalApiId().trim(),
                        item -> item,
                        (left, right) -> left
                ));
    }

    private List<PlaceSearchItemResponse> fetchKakaoFallback(String query, Double lat, Double lng) {
        if (lat != null && lng != null) {
            return kakaoPlaceSearchClient.searchByKeyword(query, lat, lng, null);
        }
        return kakaoPlaceSearchClient.searchByKeyword(query);
    }

    private List<PlaceSuggestElasticsearchClient.SuggestDocument> mergeSuggested(
            List<PlaceSuggestElasticsearchClient.SuggestDocument> fromElasticsearch,
            List<PlaceSearchItemResponse> fromKakao,
            int size
    ) {
        Map<String, PlaceSuggestElasticsearchClient.SuggestDocument> merged = new LinkedHashMap<>();
        for (PlaceSuggestElasticsearchClient.SuggestDocument item : fromElasticsearch) {
            merged.put(dedupeKey(item), item);
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
            merged.putIfAbsent(dedupeKey(document), document);
            if (merged.size() >= size * 2) {
                break;
            }
        }
        return merged.values().stream().limit(size).toList();
    }

    private String dedupeKey(PlaceSuggestElasticsearchClient.SuggestDocument item) {
        if (StringUtils.hasText(item.externalApiId())) {
            return "ext:" + item.externalApiId().trim();
        }
        if (item.lat() != null && item.lng() != null) {
            return "geo:" + normalizeText(item.name()) + ":" + round(item.lat(), 4) + ":" + round(item.lng(), 4);
        }
        return "name:" + normalizeText(item.name()) + "|" + normalizeText(item.address());
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replaceAll("\\s+", "").toLowerCase();
    }

    private double round(double value, int precision) {
        double scale = Math.pow(10, precision);
        return Math.round(value * scale) / scale;
    }

    private PlaceSearchItemResponse toResponse(
            PlaceSuggestElasticsearchClient.SuggestDocument suggested,
            Map<String, BuildingSearchProjection> registeredByExternalApiId,
            Double lat,
            Double lng
    ) {
        BuildingSearchProjection matched = null;
        if (StringUtils.hasText(suggested.externalApiId())) {
            matched = registeredByExternalApiId.get(suggested.externalApiId().trim());
        }

        String resolvedName = matched != null ? matched.getName() : suggested.name();
        String resolvedAddress = matched != null ? matched.getAddress() : suggested.address();
        Double resolvedLat = matched != null && matched.getLat() != null ? matched.getLat() : suggested.lat();
        Double resolvedLng = matched != null && matched.getLng() != null ? matched.getLng() : suggested.lng();
        boolean isRegistered = matched != null;
        Double distanceMeters = null;

        if (lat != null && lng != null && resolvedLat != null && resolvedLng != null) {
            distanceMeters = distanceInMeter(lat, lng, resolvedLat, resolvedLng);
        }

        return new PlaceSearchItemResponse(
                resolvedName,
                resolvedAddress,
                suggested.roadAddress(),
                resolvedLat,
                resolvedLng,
                isRegistered,
                suggested.externalApiId(),
                distanceMeters
        );
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
}
