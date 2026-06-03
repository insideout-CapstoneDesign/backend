package com.insideout.backend.domain.place.service;

import com.insideout.backend.domain.building.repository.BuildingRepository;
import com.insideout.backend.domain.building.repository.BuildingSearchProjection;
import com.insideout.backend.domain.place.dto.response.PlaceNearestResponse;
import com.insideout.backend.domain.place.dto.response.PlaceSearchItemResponse;
import com.insideout.backend.domain.place.exception.PlaceErrorCode;
import com.insideout.backend.domain.place.exception.PlaceException;
import com.insideout.backend.domain.map.repository.PoiRepository;
import com.insideout.backend.domain.map.repository.RegisteredPoiSearchProjection;
import com.insideout.backend.global.apiPayload.code.GeneralErrorCode;
import com.insideout.backend.global.apiPayload.exception.ProjectException;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlaceSearchService {

    private static final int DEFAULT_RADIUS_METER = 30;
    private static final int MIN_RADIUS_METER = 1;
    private static final int MAX_RADIUS_METER = 20_000;

    private final BuildingRepository buildingRepository;
    private final PoiRepository poiRepository;
    private final KakaoPlaceSearchClient kakaoPlaceSearchClient;

    public List<PlaceSearchItemResponse> search(String query, Double lat, Double lng, Integer radius) {
        if (query == null || query.isBlank()) {
            throw new ProjectException(GeneralErrorCode.BAD_REQUEST);
        }

        String normalizedQuery = query.trim();
        boolean hasCoordinate = lat != null || lng != null;

        Integer resolvedSearchRadius = null;
        if (hasCoordinate) {
            validateCoordinate(lat, lng);
            if (radius != null) {
                resolvedSearchRadius = normalizeRadius(radius);
            }
        } else if (radius != null) {
            throw new PlaceException(PlaceErrorCode.INVALID_COORDINATE);
        }

        List<PlaceSearchItemResponse> registeredPlaces = buildingRepository.searchRegisteredPlaces(normalizedQuery).stream()
                .map(this::toRegisteredSearchItem)
                .toList();

        List<PlaceSearchItemResponse> externalPlaces = hasCoordinate
                ? kakaoPlaceSearchClient.searchByKeyword(normalizedQuery, lat, lng, resolvedSearchRadius)
                : kakaoPlaceSearchClient.searchByKeyword(normalizedQuery);

        List<PlaceSearchItemResponse> merged = mergeRegisteredAndExternal(registeredPlaces, externalPlaces, normalizedQuery, lat, lng);
        if (hasCoordinate) {
            return sortWithCoordinates(merged, lat, lng, resolvedSearchRadius);
        }

        return sortWithoutCoordinates(merged, normalizedQuery);
    }

    public Optional<PlaceNearestResponse> findNearest(Double lat, Double lng, Integer radius) {
        validateCoordinate(lat, lng);
        int resolvedRadius = normalizeRadius(radius);
        Optional<PlaceNearestResponse> registeredPlace = buildingRepository
                .findNearestRegisteredPlace(lat, lng, resolvedRadius)
                .map(this::toRegisteredNearestResponse);

        if (registeredPlace.isPresent()) {
            return registeredPlace;
        }

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

    private List<PlaceSearchItemResponse> mergeRegisteredAndExternal(
            List<PlaceSearchItemResponse> registeredPlaces,
            List<PlaceSearchItemResponse> externalPlaces,
            String query,
            Double lat,
            Double lng
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
            List<PlaceSearchItemResponse> additionalRegisteredBuildings = buildingRepository
                    .findRegisteredPlacesByExternalApiIds(externalIds)
                    .stream()
                    .map(this::toRegisteredSearchItem)
                    .toList();
            additionalRegisteredBuildings.forEach(item -> registeredByExternalApiId.putIfAbsent(item.externalApiId(), item));

            List<PlaceSearchItemResponse> additionalRegisteredPois = poiRepository
                    .findRegisteredPlacesByExternalApiIds(externalIds)
                    .stream()
                    .map(this::toRegisteredSearchItem)
                    .toList();
            additionalRegisteredPois.forEach(item -> registeredByExternalApiId.putIfAbsent(item.externalApiId(), item));
        }

        for (PlaceSearchItemResponse external : externalPlaces) {
            PlaceSearchItemResponse matched = applyRegisteredMatch(external, registeredByExternalApiId);
            putBest(deduplicated, matched, query, lat, lng);
        }

        return new ArrayList<>(deduplicated.values());
    }

    private List<PlaceSearchItemResponse> sortWithoutCoordinates(List<PlaceSearchItemResponse> items, String query) {
        return items.stream()
                .sorted(Comparator
                        .comparingInt((PlaceSearchItemResponse item) -> keywordScore(item.name(), query)).reversed()
                        .thenComparing(PlaceSearchItemResponse::isRegistered, Comparator.reverseOrder())
                        .thenComparing(item -> normalizeText(item.name())))
                .toList();
    }

    private List<PlaceSearchItemResponse> sortWithCoordinates(
            List<PlaceSearchItemResponse> items,
            double lat,
            double lng,
            Integer radius
    ) {
        return items.stream()
                .map(item -> toScoredPlace(item, lat, lng))
                .filter(scored -> radius == null || (scored.distanceMeter() != null && scored.distanceMeter() <= radius))
                .sorted(Comparator
                        .comparing(ScoredPlace::distanceMeter, Comparator.nullsLast(Double::compareTo))
                        .thenComparing(scored -> scored.item().isRegistered(), Comparator.reverseOrder())
                        .thenComparing(scored -> normalizeText(scored.item().name())))
                .map(ScoredPlace::toResponse)
                .toList();
    }

    private PlaceSearchItemResponse toRegisteredSearchItem(BuildingSearchProjection building) {
        return new PlaceSearchItemResponse(
                building.getName(),
                building.getAddress(),
                null,
                building.getLat(),
                building.getLng(),
                true,
                building.getExternalApiId(),
                null
        );
    }

    private PlaceSearchItemResponse toRegisteredSearchItem(RegisteredPoiSearchProjection poi) {
        return new PlaceSearchItemResponse(
                poi.getName(),
                poi.getAddress(),
                null,
                null,
                null,
                true,
                poi.getExternalApiId(),
                null
        );
    }

    private PlaceNearestResponse toRegisteredNearestResponse(BuildingSearchProjection building) {
        return new PlaceNearestResponse(
                building.getName(),
                building.getAddress(),
                null,
                building.getLat(),
                building.getLng(),
                true,
                building.getExternalApiId()
        );
    }

    private PlaceSearchItemResponse applyRegisteredMatch(
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
                external.distanceMeters()
        );
    }

    private void putBest(
            Map<String, PlaceSearchItemResponse> deduplicated,
            PlaceSearchItemResponse candidate,
            String query,
            Double lat,
            Double lng
    ) {
        String key = dedupeKey(candidate);
        PlaceSearchItemResponse existing = deduplicated.get(key);
        if (existing == null || compareCandidate(existing, candidate, query, lat, lng) < 0) {
            deduplicated.put(key, candidate);
        }
    }

    private int compareCandidate(
            PlaceSearchItemResponse existing,
            PlaceSearchItemResponse candidate,
            String query,
            Double lat,
            Double lng
    ) {
        if (existing.isRegistered() != candidate.isRegistered()) {
            return existing.isRegistered() ? 1 : -1;
        }

        int existingScore = keywordScore(existing.name(), query);
        int candidateScore = keywordScore(candidate.name(), query);
        if (existingScore != candidateScore) {
            return Integer.compare(existingScore, candidateScore);
        }

        if (lat != null && lng != null
                && existing.lat() != null && existing.lng() != null
                && candidate.lat() != null && candidate.lng() != null) {
            double existingDistance = distanceInMeter(lat, lng, existing.lat(), existing.lng());
            double candidateDistance = distanceInMeter(lat, lng, candidate.lat(), candidate.lng());
            return Double.compare(candidateDistance, existingDistance);
        }

        boolean existingHasCoordinate = existing.lat() != null && existing.lng() != null;
        boolean candidateHasCoordinate = candidate.lat() != null && candidate.lng() != null;
        if (existingHasCoordinate != candidateHasCoordinate) {
            return existingHasCoordinate ? 1 : -1;
        }

        return 0;
    }

    private String dedupeKey(PlaceSearchItemResponse item) {
        if (StringUtils.hasText(item.externalApiId())) {
            return "ext:" + item.externalApiId().trim();
        }
        if (item.lat() != null && item.lng() != null) {
            return "geo:" + normalizeText(item.name()) + ":" + round(item.lat(), 4) + ":" + round(item.lng(), 4);
        }
        return "name:" + normalizeText(item.name()) + "|" + normalizeText(item.address());
    }

    private int keywordScore(String name, String query) {
        String target = normalizeText(name);
        String keyword = normalizeText(query);
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

    private ScoredPlace toScoredPlace(PlaceSearchItemResponse item, double lat, double lng) {
        if (item.lat() == null || item.lng() == null) {
            return new ScoredPlace(item, null);
        }

        return new ScoredPlace(item, distanceInMeter(lat, lng, item.lat(), item.lng()));
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
                    distanceMeter
            );
        }
    }
}
