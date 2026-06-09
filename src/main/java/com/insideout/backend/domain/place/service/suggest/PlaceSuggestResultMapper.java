package com.insideout.backend.domain.place.service.suggest;

import com.insideout.backend.domain.building.repository.BuildingRepository;
import com.insideout.backend.domain.map.repository.PoiRepository;
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

    static Map<String, PlaceSearchItemResponse> resolveRegisteredMap(
            List<PlaceSuggestElasticsearchClient.SuggestDocument> suggested,
            BuildingRepository buildingRepository,
            PoiRepository poiRepository
    ) {
        Set<String> externalApiIds = suggested.stream()
                .map(PlaceSuggestElasticsearchClient.SuggestDocument::externalApiId)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toSet());

        if (externalApiIds.isEmpty()) {
            return Map.of();
        }

        Map<String, PlaceSearchItemResponse> registered = new java.util.LinkedHashMap<>();
        buildingRepository.findRegisteredPlacesByExternalApiIds(externalApiIds).stream()
                .map(item -> new PlaceSearchItemResponse(
                        item.getName(),
                        item.getAddress(),
                        null,
                        item.getLat(),
                        item.getLng(),
                        true,
                        item.getExternalApiId(),
                        null,
                        null,
                        null,
                        item.getId(),
                        null
                ))
                .forEach(item -> registered.putIfAbsent(item.externalApiId(), item));
        poiRepository.findRegisteredPlacesByExternalApiIds(externalApiIds).stream()
                .map(item -> new PlaceSearchItemResponse(
                        item.getName(),
                        item.getAddress(),
                        null,
                        item.getLat(),
                        item.getLng(),
                        true,
                        item.getExternalApiId(),
                        null,
                        item.getBuildingName(),
                        null,
                        item.getBuildingId(),
                        item.getPoiId()
                ))
                .forEach(item -> registered.putIfAbsent(item.externalApiId(), item));
        return registered.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        java.util.LinkedHashMap::new
                ));
    }

    static PlaceSearchItemResponse toResponse(
            PlaceSuggestElasticsearchClient.SuggestDocument suggested,
            Map<String, PlaceSearchItemResponse> registeredByExternalApiId,
            Double lat,
            Double lng
    ) {
        PlaceSearchItemResponse matched = null;
        if (StringUtils.hasText(suggested.externalApiId())) {
            matched = registeredByExternalApiId.get(suggested.externalApiId().trim());
        }

        String resolvedName = matched != null ? matched.name() : suggested.name();
        String resolvedAddress = matched != null ? matched.address() : suggested.address();
        Double resolvedLat = matched != null && matched.lat() != null ? matched.lat() : suggested.lat();
        Double resolvedLng = matched != null && matched.lng() != null ? matched.lng() : suggested.lng();
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
                distanceMeters,
                matched != null ? matched.parentBuildingName() : null,
                matched != null ? matched.displayName() : null,
                matched != null ? matched.placeId() : null,
                matched != null ? matched.poiId() : null
        );
    }
}
