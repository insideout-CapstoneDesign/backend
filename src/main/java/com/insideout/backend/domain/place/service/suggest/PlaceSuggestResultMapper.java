package com.insideout.backend.domain.place.service.suggest;

import com.insideout.backend.domain.building.repository.BuildingRepository;
import com.insideout.backend.domain.building.repository.BuildingSearchProjection;
import com.insideout.backend.domain.place.dto.response.PlaceSearchItemResponse;
import com.insideout.backend.domain.place.service.es.PlaceSuggestElasticsearchClient;
import com.insideout.backend.domain.place.service.support.PlaceSearchSupport;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class PlaceSuggestResultMapper {

    private PlaceSuggestResultMapper() {
    }

    static Map<String, BuildingSearchProjection> resolveRegisteredMap(
            List<PlaceSuggestElasticsearchClient.SuggestDocument> suggested,
            BuildingRepository buildingRepository
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

    static PlaceSearchItemResponse toResponse(
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
}

