package com.insideout.backend.domain.place.service.suggest;

import com.insideout.backend.domain.building.repository.BuildingRepository;
import com.insideout.backend.domain.building.repository.BuildingSearchProjection;
import com.insideout.backend.domain.place.dto.response.PlaceSearchItemResponse;
import com.insideout.backend.domain.place.exception.PlaceErrorCode;
import com.insideout.backend.domain.place.exception.PlaceException;
import com.insideout.backend.domain.place.service.es.PlaceSuggestElasticsearchClient;
import com.insideout.backend.domain.place.service.kakao.KakaoPlaceSearchClient;
import com.insideout.backend.domain.place.service.search.PlaceSearchIndexingService;
import com.insideout.backend.domain.place.service.support.PlaceSearchSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
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
    private static final int MIN_NEARBY_FALLBACK_SIZE = 10;

    private final PlaceSuggestElasticsearchClient placeSuggestElasticsearchClient;
    private final KakaoPlaceSearchClient kakaoPlaceSearchClient;
    private final PlaceSearchIndexingService placeSearchIndexingService;
    private final BuildingRepository buildingRepository;

    public List<PlaceSearchItemResponse> suggest(String query, Double lat, Double lng, Integer size) {
        String normalizedQuery = validateAndNormalizeQuery(query);
        PlaceSearchSupport.validateCoordinate(lat, lng, true);
        int resolvedSize = normalizeSize(size);

        List<PlaceSuggestElasticsearchClient.SuggestDocument> fromElasticsearch = placeSuggestElasticsearchClient
                .suggest(normalizedQuery, resolvedSize, lat, lng);

        List<PlaceSuggestElasticsearchClient.SuggestDocument> mergedSuggested = fromElasticsearch;
        int fallbackSize = resolveFallbackSize(resolvedSize, fromElasticsearch.size(), lat, lng);
        if (fallbackSize > 0) {
            List<PlaceSearchItemResponse> fromKakao = fetchKakaoFallback(normalizedQuery, lat, lng, fallbackSize);
            mergedSuggested = mergeSuggested(fromElasticsearch, fromKakao, resolvedSize);
            if (!fromKakao.isEmpty()) {
                placeSearchIndexingService.upsertFromSearchResultsAsync(fromKakao.stream().limit(fallbackSize).toList());
            }
        }

        if (mergedSuggested.isEmpty()) {
            return List.of();
        }

        Map<String, BuildingSearchProjection> registeredByExternalApiId = resolveRegisteredMap(mergedSuggested);

        return mergedSuggested.stream()
                .map(item -> toResponse(item, registeredByExternalApiId, lat, lng))
                .sorted(buildComparator(normalizedQuery, lat, lng))
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

    private int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }

        if (size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private int resolveFallbackSize(int resolvedSize, int esResultSize, Double lat, Double lng) {
        int needed = Math.max(0, resolvedSize - esResultSize);
        if (lat == null || lng == null) {
            return needed;
        }
        int nearbyFallback = Math.min(resolvedSize, MIN_NEARBY_FALLBACK_SIZE);
        return Math.max(needed, nearbyFallback);
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

    private List<PlaceSearchItemResponse> fetchKakaoFallback(String query, Double lat, Double lng, int size) {
        if (lat != null && lng != null) {
            return kakaoPlaceSearchClient.searchByKeyword(query, lat, lng, null, size);
        }
        return kakaoPlaceSearchClient.searchByKeyword(query, null, null, null, size);
    }

    private List<PlaceSuggestElasticsearchClient.SuggestDocument> mergeSuggested(
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
            distanceMeters = PlaceSearchSupport.distanceInMeter(lat, lng, resolvedLat, resolvedLng);
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

    private Comparator<PlaceSearchItemResponse> buildComparator(String query, Double lat, Double lng) {
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

    private int keywordScore(String name, String query) {
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
