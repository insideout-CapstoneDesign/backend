package com.insideout.backend.domain.place.service.suggest;

import com.insideout.backend.domain.building.repository.BuildingRepository;
import com.insideout.backend.domain.map.repository.PoiRepository;
import com.insideout.backend.domain.place.dto.response.PlaceSearchItemResponse;
import com.insideout.backend.domain.place.exception.PlaceErrorCode;
import com.insideout.backend.domain.place.exception.PlaceException;
import com.insideout.backend.domain.place.service.es.PlaceSuggestElasticsearchClient;
import com.insideout.backend.domain.place.service.kakao.KakaoPlaceSearchClient;
import com.insideout.backend.domain.place.service.search.PlaceSearchIndexingService;
import com.insideout.backend.domain.place.service.support.PlaceSearchSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class PlaceSuggestService {

    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 50;
    private static final int MIN_QUERY_LENGTH = 2;

    private final PlaceSuggestElasticsearchClient placeSuggestElasticsearchClient;
    private final KakaoPlaceSearchClient kakaoPlaceSearchClient;
    private final PlaceSearchIndexingService placeSearchIndexingService;
    private final BuildingRepository buildingRepository;
    private final PoiRepository poiRepository;

    public List<PlaceSearchItemResponse> suggest(String query, Double lat, Double lng, Integer size) {
        String normalizedQuery = validateAndNormalizeQuery(query);
        PlaceSearchSupport.validateCoordinate(lat, lng, true);
        int resolvedSize = normalizeSize(size);

        List<PlaceSuggestElasticsearchClient.SuggestDocument> fromElasticsearch;
        try {
            fromElasticsearch = placeSuggestElasticsearchClient
                    .suggest(normalizedQuery, resolvedSize, lat, lng);
        } catch (PlaceException ex) {
            if (ex.getErrorCode() != PlaceErrorCode.SEARCH_SERVICE_UNAVAILABLE) {
                throw ex;
            }
            log.warn("Elasticsearch unavailable for suggest query='{}' lat={} lng={} size={}. Falling back to Kakao.",
                    normalizedQuery, lat, lng, resolvedSize, ex);
            fromElasticsearch = List.of();
        }

        List<PlaceSuggestElasticsearchClient.SuggestDocument> mergedSuggested = fromElasticsearch;
        int fallbackSize = PlaceSuggestFallbackPolicy.resolveFallbackSize(resolvedSize, fromElasticsearch.size(), lat, lng);
        if (fallbackSize > 0) {
            try {
                List<PlaceSearchItemResponse> fromKakao = fetchKakaoFallback(normalizedQuery, lat, lng);
                mergedSuggested = PlaceSuggestDocumentMerger.merge(fromElasticsearch, fromKakao, resolvedSize);
                if (!fromKakao.isEmpty()) {
                    placeSearchIndexingService.upsertFromSearchResultsAsync(fromKakao.stream().limit(fallbackSize).toList());
                }
            } catch (PlaceException ex) {
                if (ex.getErrorCode() == PlaceErrorCode.KAKAO_LOCAL_API_UNAVAILABLE) {
                    log.warn("Kakao fallback unavailable for suggest query='{}' lat={} lng={} size={}. Returning ES results only.",
                            normalizedQuery, lat, lng, fallbackSize, ex);
                    mergedSuggested = fromElasticsearch;
                } else {
                    throw ex;
                }
            }
        }

        if (mergedSuggested.isEmpty()) {
            return List.of();
        }

        Map<String, PlaceSearchItemResponse> registeredByExternalApiId =
                PlaceSuggestResultMapper.resolveRegisteredMap(mergedSuggested, buildingRepository, poiRepository);

        return mergedSuggested.stream()
                .map(item -> PlaceSuggestResultMapper.toResponse(item, registeredByExternalApiId, lat, lng))
                .sorted(PlaceSuggestResultSorter.comparator(normalizedQuery, lat, lng))
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

    private List<PlaceSearchItemResponse> fetchKakaoFallback(String query, Double lat, Double lng) {
        if (lat != null && lng != null) {
            return kakaoPlaceSearchClient.searchByKeyword(query, lat, lng, null);
        }
        return kakaoPlaceSearchClient.searchByKeyword(query, null, null, null);
    }

}
