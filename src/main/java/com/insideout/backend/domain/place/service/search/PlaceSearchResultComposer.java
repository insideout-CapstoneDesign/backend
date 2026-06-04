package com.insideout.backend.domain.place.service.search;

import com.insideout.backend.domain.building.repository.BuildingRepository;
import com.insideout.backend.domain.building.repository.BuildingSearchProjection;
import com.insideout.backend.domain.map.repository.PoiRepository;
import com.insideout.backend.domain.map.repository.RegisteredPoiSearchProjection;
import com.insideout.backend.domain.place.dto.response.PlaceSearchItemResponse;
import com.insideout.backend.domain.place.service.support.PlaceSearchSupport;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class PlaceSearchResultComposer {

    private PlaceSearchResultComposer() {
    }

    static List<PlaceSearchItemResponse> mergeAndSort(
            List<PlaceSearchItemResponse> registeredPlaces,
            List<PlaceSearchItemResponse> externalPlaces,
            String query,
            Double lat,
            Double lng,
            Integer radius,
            int size,
            BuildingRepository buildingRepository,
            PoiRepository poiRepository
    ) {
        List<PlaceSearchItemResponse> merged = mergeRegisteredAndExternal(
                registeredPlaces,
                externalPlaces,
                query,
                lat,
                lng,
                buildingRepository,
                poiRepository
        );

        if (lat != null && lng != null) {
            return sortWithCoordinates(merged, query, lat, lng, radius, size);
        }

        return sortWithoutCoordinates(merged, query).stream()
                .limit(size)
                .toList();
    }

    static PlaceSearchItemResponse toRegisteredSearchItem(BuildingSearchProjection building) {
        return new PlaceSearchItemResponse(
                building.getName(),
                building.getAddress(),
                null,
                building.getLat(),
                building.getLng(),
                true,
                building.getExternalApiId(),
                null,
                null,
                building.getName()
        );
    }

    static PlaceSearchItemResponse toRegisteredPoiSearchItem(RegisteredPoiSearchProjection poi) {
        return new PlaceSearchItemResponse(
                poi.getName(),
                poi.getAddress(),
                null,
                null,
                null,
                true,
                poi.getExternalApiId(),
                null,
                poi.getBuildingName(),
                null
        );
    }

    private static List<PlaceSearchItemResponse> mergeRegisteredAndExternal(
            List<PlaceSearchItemResponse> registeredPlaces,
            List<PlaceSearchItemResponse> externalPlaces,
            String query,
            Double lat,
            Double lng,
            BuildingRepository buildingRepository,
            PoiRepository poiRepository
    ) {
        Map<String, PlaceSearchItemResponse> registeredByExternalApiId = registeredPlaces.stream()
                .filter(item -> StringUtils.hasText(item.externalApiId()))
                .collect(Collectors.toMap(
                        item -> item.externalApiId().trim(),
                        item -> item,
                        (left, right) -> left
                ));

        Map<String, PlaceSearchItemResponse> deduplicated = new LinkedHashMap<>();
        registeredPlaces.forEach(item -> putBest(deduplicated, item, query, lat, lng));

        Set<String> externalIds = externalPlaces.stream()
                .map(PlaceSearchItemResponse::externalApiId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        if (!externalIds.isEmpty()) {
            List<PlaceSearchItemResponse> additionalRegistered = buildingRepository
                    .findRegisteredPlacesByExternalApiIds(externalIds)
                    .stream()
                    .map(PlaceSearchResultComposer::toRegisteredSearchItem)
                    .toList();
            additionalRegistered.forEach(item -> registeredByExternalApiId.putIfAbsent(item.externalApiId(), item));

            List<PlaceSearchItemResponse> additionalRegisteredPois = poiRepository
                    .findRegisteredPlacesByExternalApiIds(externalIds)
                    .stream()
                    .map(PlaceSearchResultComposer::toRegisteredPoiSearchItem)
                    .toList();
            additionalRegisteredPois.forEach(item -> registeredByExternalApiId.putIfAbsent(item.externalApiId(), item));
        }

        for (PlaceSearchItemResponse external : externalPlaces) {
            PlaceSearchItemResponse matched = applyRegisteredMatch(external, registeredByExternalApiId);
            putBest(deduplicated, matched, query, lat, lng);
        }

        return new ArrayList<>(deduplicated.values());
    }

    private static PlaceSearchItemResponse applyRegisteredMatch(
            PlaceSearchItemResponse external,
            Map<String, PlaceSearchItemResponse> registeredByExternalApiId
    ) {
        if (!StringUtils.hasText(external.externalApiId())) {
            return external;
        }

        PlaceSearchItemResponse registered = registeredByExternalApiId.get(external.externalApiId().trim());
        if (registered == null) {
            return external;
        }

        return new PlaceSearchItemResponse(
                registered.name(),
                registered.address(),
                external.roadAddress(),
                registered.lat() != null ? registered.lat() : external.lat(),
                registered.lng() != null ? registered.lng() : external.lng(),
                true,
                registered.externalApiId(),
                external.distanceMeters(),
                registered.parentBuildingName(),
                registered.displayName()
        );
    }

    private static List<PlaceSearchItemResponse> sortWithoutCoordinates(List<PlaceSearchItemResponse> items, String query) {
        return items.stream()
                .sorted(Comparator
                        .comparingInt((PlaceSearchItemResponse item) -> PlaceSearchSupport.canonicalKeywordScore(item, query)).reversed()
                        .thenComparing(Comparator.comparingInt((PlaceSearchItemResponse item) -> PlaceSearchSupport.displayKeywordScore(item, query)).reversed())
                        .thenComparing(item -> StringUtils.hasText(item.parentBuildingName()))
                        .thenComparing(PlaceSearchItemResponse::isRegistered, Comparator.reverseOrder())
                        .thenComparing(item -> PlaceSearchSupport.normalizeText(PlaceSearchSupport.displayLabel(item))))
                .toList();
    }

    private static List<PlaceSearchItemResponse> sortWithCoordinates(
            List<PlaceSearchItemResponse> items,
            String query,
            double lat,
            double lng,
            Integer radius,
            int size
    ) {
        return items.stream()
                .map(item -> toScoredPlace(item, lat, lng))
                .filter(scored -> radius == null || (scored.distanceMeter() != null && scored.distanceMeter() <= radius))
                .sorted(Comparator
                        .comparingInt((ScoredPlace scored) -> PlaceSearchSupport.canonicalKeywordScore(scored.item(), query)).reversed()
                        .thenComparing(Comparator.comparingInt((ScoredPlace scored) -> PlaceSearchSupport.displayKeywordScore(scored.item(), query)).reversed())
                        .thenComparing(scored -> StringUtils.hasText(scored.item().parentBuildingName()))
                        .thenComparing(ScoredPlace::distanceMeter, Comparator.nullsLast(Double::compareTo))
                        .thenComparing(scored -> scored.item().isRegistered(), Comparator.reverseOrder())
                        .thenComparing(scored -> PlaceSearchSupport.normalizeText(PlaceSearchSupport.displayLabel(scored.item()))))
                .limit(size)
                .map(ScoredPlace::toResponse)
                .toList();
    }

    private static void putBest(
            Map<String, PlaceSearchItemResponse> deduplicated,
            PlaceSearchItemResponse candidate,
            String query,
            Double lat,
            Double lng
    ) {
        String key = PlaceSearchSupport.dedupeKey(candidate);
        PlaceSearchItemResponse existing = deduplicated.get(key);
        if (existing == null || compareCandidate(existing, candidate, query, lat, lng) < 0) {
            deduplicated.put(key, candidate);
        }
    }

    private static int compareCandidate(
            PlaceSearchItemResponse existing,
            PlaceSearchItemResponse candidate,
            String query,
            Double lat,
            Double lng
    ) {
        if (existing.isRegistered() != candidate.isRegistered()) {
            return existing.isRegistered() ? 1 : -1;
        }

        int existingScore = PlaceSearchSupport.canonicalKeywordScore(existing, query);
        int candidateScore = PlaceSearchSupport.canonicalKeywordScore(candidate, query);
        if (existingScore != candidateScore) {
            return Integer.compare(existingScore, candidateScore);
        }

        int existingDisplayScore = PlaceSearchSupport.displayKeywordScore(existing, query);
        int candidateDisplayScore = PlaceSearchSupport.displayKeywordScore(candidate, query);
        if (existingDisplayScore != candidateDisplayScore) {
            return Integer.compare(existingDisplayScore, candidateDisplayScore);
        }

        boolean existingIsPoi = StringUtils.hasText(existing.parentBuildingName());
        boolean candidateIsPoi = StringUtils.hasText(candidate.parentBuildingName());
        if (existingIsPoi != candidateIsPoi) {
            return existingIsPoi ? -1 : 1;
        }

        if (lat != null && lng != null
                && existing.lat() != null && existing.lng() != null
                && candidate.lat() != null && candidate.lng() != null) {
            double existingDistance = PlaceSearchSupport.distanceInMeter(lat, lng, existing.lat(), existing.lng());
            double candidateDistance = PlaceSearchSupport.distanceInMeter(lat, lng, candidate.lat(), candidate.lng());
            return Double.compare(candidateDistance, existingDistance);
        }

        boolean existingHasCoordinate = existing.lat() != null && existing.lng() != null;
        boolean candidateHasCoordinate = candidate.lat() != null && candidate.lng() != null;
        if (existingHasCoordinate != candidateHasCoordinate) {
            return existingHasCoordinate ? 1 : -1;
        }

        return 0;
    }

    private static ScoredPlace toScoredPlace(PlaceSearchItemResponse item, double lat, double lng) {
        if (item.lat() == null || item.lng() == null) {
            return new ScoredPlace(item, null);
        }
        return new ScoredPlace(item, PlaceSearchSupport.distanceInMeter(lat, lng, item.lat(), item.lng()));
    }

    private record ScoredPlace(PlaceSearchItemResponse item, Double distanceMeter) {
        PlaceSearchItemResponse toResponse() {
            return new PlaceSearchItemResponse(
                    item.name(),
                    item.address(),
                    item.roadAddress(),
                    item.lat(),
                    item.lng(),
                    item.isRegistered(),
                    item.externalApiId(),
                    distanceMeter,
                    item.parentBuildingName(),
                    item.displayName()
            );
        }
    }
}
