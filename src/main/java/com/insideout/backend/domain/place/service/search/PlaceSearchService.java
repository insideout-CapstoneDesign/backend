package com.insideout.backend.domain.place.service.search;

import com.insideout.backend.domain.building.repository.BuildingRepository;
import com.insideout.backend.domain.building.repository.BuildingDirectoryRepository;
import com.insideout.backend.domain.building.repository.BuildingSearchProjection;
import com.insideout.backend.domain.building.entity.BuildingDirectory;
import com.insideout.backend.domain.map.repository.PoiRepository;
import com.insideout.backend.domain.place.dto.response.PlaceNearestResponse;
import com.insideout.backend.domain.place.dto.response.PlaceSearchItemResponse;
import com.insideout.backend.domain.place.exception.PlaceErrorCode;
import com.insideout.backend.domain.place.exception.PlaceException;
import com.insideout.backend.domain.place.service.es.PlaceSuggestElasticsearchClient;
import com.insideout.backend.domain.place.service.kakao.KakaoPlaceSearchClient;
import com.insideout.backend.domain.place.service.support.PlaceSearchSupport;
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
    private static final int DEFAULT_SEARCH_SIZE = 15;
    private static final int MAX_SEARCH_SIZE = 100;

    private final BuildingRepository buildingRepository;
    private final BuildingDirectoryRepository buildingDirectoryRepository;
    private final PoiRepository poiRepository;
    private final PlaceSuggestElasticsearchClient placeSuggestElasticsearchClient;
    private final KakaoPlaceSearchClient kakaoPlaceSearchClient;
    private final PlaceSearchIndexingService placeSearchIndexingService;

    public List<PlaceSearchItemResponse> search(String query, Double lat, Double lng, Integer radius, Integer size) {
        if (query == null || query.isBlank()) {
            throw new ProjectException(GeneralErrorCode.BAD_REQUEST);
        }

        String normalizedQuery = query.trim();
        int resolvedSize = normalizeSize(size);
        boolean hasCoordinate = lat != null || lng != null;

        Integer resolvedSearchRadius = null;
        if (hasCoordinate) {
            PlaceSearchSupport.validateCoordinate(lat, lng, false);
            if (radius != null) {
                resolvedSearchRadius = normalizeRadius(radius);
            }
        } else if (radius != null) {
            throw new PlaceException(PlaceErrorCode.INVALID_COORDINATE);
        }

        List<PlaceSearchItemResponse> registeredPlaces = buildingRepository.searchRegisteredPlaces(normalizedQuery).stream()
                .map(PlaceSearchResultComposer::toRegisteredSearchItem)
                .toList();

        List<PlaceSearchItemResponse> externalPlaces = PlaceSearchExternalPipeline.fetchExternalPlaces(
                normalizedQuery,
                lat,
                lng,
                resolvedSearchRadius,
                hasCoordinate,
                resolvedSize,
                placeSuggestElasticsearchClient,
                kakaoPlaceSearchClient,
                placeSearchIndexingService
        );

        return PlaceSearchResultComposer.mergeAndSort(
                registeredPlaces,
                externalPlaces,
                normalizedQuery,
                lat,
                lng,
                resolvedSearchRadius,
                resolvedSize,
                buildingRepository,
                poiRepository
        );
    }

    public Optional<PlaceNearestResponse> findNearest(Double lat, Double lng, Integer radius) {
        PlaceSearchSupport.validateCoordinate(lat, lng, false);
        int resolvedRadius = normalizeRadius(radius);
        Optional<PlaceNearestResponse> registeredPlace = buildingRepository
                .findNearestRegisteredPlace(lat, lng, resolvedRadius)
                .map(this::toRegisteredNearestResponse);

        if (registeredPlace.isPresent()) {
            return registeredPlace;
        }

        Optional<PlaceNearestResponse> directoryMatchedPlace = buildingDirectoryRepository
                .findNearestPublicBuilding(lng, lat, resolvedRadius)
                .flatMap(directory -> buildingRepository.findById(directory.getId())
                        .map(building -> toRegisteredNearestResponse(directory, building))
                        .or(() -> Optional.of(toRegisteredNearestResponse(directory, null))));

        if (directoryMatchedPlace.isPresent()) {
            return directoryMatchedPlace;
        }

        return kakaoPlaceSearchClient.findNearestByCoordinate(lat, lng, resolvedRadius);
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

    private int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_SEARCH_SIZE;
        }
        if (size < 1) {
            return DEFAULT_SEARCH_SIZE;
        }
        return Math.min(size, MAX_SEARCH_SIZE);
    }

    private PlaceNearestResponse toRegisteredNearestResponse(BuildingSearchProjection building) {
        return new PlaceNearestResponse(
                building.getName(),
                building.getAddress(),
                null,
                building.getLat(),
                building.getLng(),
                true,
                building.getExternalApiId(),
                building.getId(),
                null
        );
    }

    private PlaceNearestResponse toRegisteredNearestResponse(BuildingDirectory directory, com.insideout.backend.domain.building.entity.Building building) {
        String externalApiId = building != null ? building.getExternalApiId() : null;
        Double lat = directory.getCentroid() != null ? directory.getCentroid().getY() : null;
        Double lng = directory.getCentroid() != null ? directory.getCentroid().getX() : null;

        return new PlaceNearestResponse(
                directory.getName(),
                directory.getAddress(),
                null,
                lat,
                lng,
                true,
                externalApiId,
                directory.getId(),
                null
        );
    }
}
