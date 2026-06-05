package com.insideout.backend.domain.place.service.detail;

import com.insideout.backend.domain.building.entity.Building;
import com.insideout.backend.domain.building.entity.BuildingDirectory;
import com.insideout.backend.domain.building.entity.Floor;
import com.insideout.backend.domain.building.entity.Floorplan;
import com.insideout.backend.domain.building.repository.BuildingDirectoryRepository;
import com.insideout.backend.domain.building.repository.BuildingRepository;
import com.insideout.backend.domain.building.repository.FloorRepository;
import com.insideout.backend.domain.building.repository.FloorplanRepository;
import com.insideout.backend.domain.map.entity.PoiCategory;
import com.insideout.backend.domain.map.entity.MapVersion;
import com.insideout.backend.domain.map.entity.Poi;
import com.insideout.backend.domain.map.enums.MapType;
import com.insideout.backend.domain.map.repository.PoiCategoryRepository;
import com.insideout.backend.domain.map.repository.MapVersionRepository;
import com.insideout.backend.domain.map.repository.PoiRepository;
import com.insideout.backend.domain.place.dto.response.PlaceDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceDetailService {

    private final BuildingRepository buildingRepository;
    private final BuildingDirectoryRepository buildingDirectoryRepository;
    private final FloorRepository floorRepository;
    private final FloorplanRepository floorplanRepository;
    private final PoiCategoryRepository poiCategoryRepository;
    private final MapVersionRepository mapVersionRepository;
    private final PoiRepository poiRepository;

    public Optional<PlaceDetailResponse> getDetail(String placeId, String externalApiId) {
        if (!StringUtils.hasText(placeId) && !StringUtils.hasText(externalApiId)) {
            return Optional.empty();
        }

        ResolvedPlace resolvedPlace = resolvePlace(placeId, externalApiId);
        if (resolvedPlace == null) {
            return Optional.empty();
        }

        BuildingDirectory buildingDirectory = buildingDirectoryRepository
                .findByIdAndIsPublicTrue(resolvedPlace.building().getId())
                .orElse(null);
        if (buildingDirectory == null) {
            return Optional.empty();
        }

        MapVersion publishedMapVersion = resolvePublishedMapVersion(resolvedPlace, buildingDirectory);
        boolean hasIndoorMap = false;
        List<PlaceDetailResponse.FloorResponse> floors = List.of();

        if (publishedMapVersion != null) {
            List<Floor> allFloors = floorRepository.findAllByBuilding_IdOrderByLevelDesc(resolvedPlace.building().getId());
            if (!allFloors.isEmpty()) {
                List<UUID> floorIds = allFloors.stream()
                        .map(Floor::getId)
                        .toList();

                Set<UUID> currentFloorIds = floorplanRepository.findAllByFloorIdInAndIsCurrentTrue(floorIds).stream()
                        .map(Floorplan::getFloor)
                        .filter(floor -> floor != null && floor.getId() != null)
                        .map(Floor::getId)
                        .collect(Collectors.toSet());

                List<Poi> publishedPois = poiRepository.findAllByMapVersionIdWithFloor(publishedMapVersion.getId());
                Map<UUID, List<Poi>> poisByFloorId = filterDisplayablePois(publishedPois).stream()
                        .collect(Collectors.groupingBy(
                                poi -> poi.getFloor().getId(),
                                LinkedHashMap::new,
                                Collectors.toList()
                        ));

                floors = allFloors.stream()
                        .map(floor -> new PlaceDetailResponse.FloorResponse(
                                floor.getId(),
                                floor.getLevel(),
                                floor.getName(),
                                toPoiResponses(poisByFloorId.getOrDefault(floor.getId(), List.of()))
                        ))
                        .toList();
                hasIndoorMap = floors.stream().anyMatch(floor -> !floor.pois().isEmpty())
                        || !currentFloorIds.isEmpty();
            }
        }

        return Optional.of(new PlaceDetailResponse(
                resolvedPlace.placeId(),
                resolvedPlace.poiId(),
                resolvedPlace.externalApiId(),
                resolvedPlace.name(),
                resolvedPlace.address(),
                resolvedPlace.isRegistered(),
                hasIndoorMap,
                floors
        ));
    }

    private ResolvedPlace resolvePlace(String placeId, String externalApiId) {
        if (StringUtils.hasText(placeId)) {
            String normalizedPlaceId = placeId.trim();
            Optional<ResolvedPlace> resolvedByUuid = resolvePlaceByUuid(normalizedPlaceId);
            if (resolvedByUuid.isPresent()) {
                return resolvedByUuid.get();
            }

            Optional<ResolvedPlace> resolvedByNumericPublicId = resolvePlaceByNumericPublicId(normalizedPlaceId);
            if (resolvedByNumericPublicId.isPresent()) {
                return resolvedByNumericPublicId.get();
            }

            Optional<ResolvedPlace> resolvedByExternalApiId = resolvePlaceByExternalApiId(normalizedPlaceId);
            if (resolvedByExternalApiId.isPresent()) {
                return resolvedByExternalApiId.get();
            }
        }

        String normalizedExternalApiId = externalApiId == null ? null : externalApiId.trim();
        if (!StringUtils.hasText(normalizedExternalApiId)) {
            return null;
        }

        return resolvePlaceByExternalApiId(normalizedExternalApiId).orElse(null);
    }

    private Optional<ResolvedPlace> resolvePlaceByUuid(String placeId) {
        try {
            UUID uuid = UUID.fromString(placeId);
            return buildingRepository.findById(uuid)
                    .map(building -> toResolvedPlace(building, null))
                    .or(() -> poiRepository.findByIdWithFloorAndMapVersion(uuid)
                            .map(poi -> toResolvedPlace(poi.getFloor().getBuilding(), poi)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private Optional<ResolvedPlace> resolvePlaceByNumericPublicId(String placeId) {
        try {
            Long publicId = Long.parseLong(placeId);
            return poiRepository.findByPublicId(publicId)
                    .map(poi -> toResolvedPlace(poi.getFloor().getBuilding(), poi));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private Optional<ResolvedPlace> resolvePlaceByExternalApiId(String externalApiId) {
        if (!StringUtils.hasText(externalApiId)) {
            return Optional.empty();
        }

        return buildingRepository.findFirstByExternalApiId(externalApiId)
                .map(building -> toResolvedPlace(building, null))
                .or(() -> poiRepository.findFirstByExternalApiIdWithFloorAndMapVersion(externalApiId)
                        .map(poi -> toResolvedPlace(poi.getFloor().getBuilding(), poi)));
    }

    private ResolvedPlace toResolvedPlace(Building building, Poi selectedPoi) {
        BuildingDirectory buildingDirectory = buildingDirectoryRepository.findByIdAndIsPublicTrue(building.getId()).orElse(null);
        String selectedName = buildingDirectory != null ? buildingDirectory.getName() : building.getName();
        String selectedAddress = buildingDirectory != null ? buildingDirectory.getAddress() : building.getAddress();
        String selectedExternalApiId = building.getExternalApiId() != null
                ? building.getExternalApiId()
                : selectedPoi != null ? selectedPoi.getExternalApiId() : null;
        boolean isRegistered = buildingDirectory != null && buildingDirectory.isPublic();

        return new ResolvedPlace(
                building.getId(),
                selectedPoi != null ? selectedPoi.getId() : null,
                selectedExternalApiId,
                selectedName,
                selectedAddress,
                building,
                selectedPoi,
                isRegistered
        );
    }

    private MapVersion resolvePublishedMapVersion(ResolvedPlace resolvedPlace, BuildingDirectory buildingDirectory) {
        if (resolvedPlace.selectedPoi() != null && resolvedPlace.selectedPoi().getMapVersion() != null) {
            return resolvedPlace.selectedPoi().getMapVersion();
        }

        if (buildingDirectory.getPublishedVersion() != null) {
            return buildingDirectory.getPublishedVersion();
        }

        return mapVersionRepository.findFirstByBuildingIdAndMapTypeAndStatusOrderByCreatedAtDesc(
                resolvedPlace.building().getId(),
                MapType.BUILDING,
                "published"
        ).orElse(null);
    }

    private List<PlaceDetailResponse.PoiResponse> toPoiResponses(List<Poi> pois) {
        return pois.stream()
                .map(poi -> new PlaceDetailResponse.PoiResponse(
                        poi.getId(),
                        poi.getName(),
                        poi.getFloor() == null ? null : poi.getFloor().getName(),
                        poi.getExternalApiId()
                ))
                .toList();
    }

    private List<Poi> filterDisplayablePois(List<Poi> pois) {
        if (pois == null || pois.isEmpty()) {
            return List.of();
        }

        Set<Long> categoryIds = pois.stream()
                .map(Poi::getCategoryId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        if (categoryIds.isEmpty()) {
            return pois.stream()
                    .filter(this::looksLikeStorePoi)
                    .toList();
        }

        Set<Long> facilityCategoryIds = poiCategoryRepository.findAllById(categoryIds).stream()
                .filter(category -> category.getCode() != null && category.getCode().startsWith("facility."))
                .map(PoiCategory::getId)
                .collect(Collectors.toSet());
        Set<Long> storeCategoryIds = poiCategoryRepository.findAllById(categoryIds).stream()
                .filter(category -> category.getCode() != null && category.getCode().startsWith("store."))
                .map(PoiCategory::getId)
                .collect(Collectors.toSet());

        return pois.stream()
                .filter(poi -> {
                    Long categoryId = poi.getCategoryId();
                    if (categoryId == null) {
                        return looksLikeStorePoi(poi);
                    }
                    if (facilityCategoryIds.contains(categoryId)) {
                        return false;
                    }
                    if (storeCategoryIds.contains(categoryId)) {
                        return true;
                    }
                    return looksLikeStorePoi(poi);
                })
                .toList();
    }

    private boolean looksLikeStorePoi(Poi poi) {
        if (poi == null || poi.getName() == null) {
            return false;
        }

        String normalized = poi.getName().toLowerCase(Locale.ROOT).replace(" ", "");
        return !normalized.contains("elevator")
                && !normalized.contains("escalator")
                && !normalized.contains("restroom")
                && !normalized.contains("화장실")
                && !normalized.contains("엘리베이터")
                && !normalized.contains("에스컬레이터")
                && !normalized.contains("계단")
                && !normalized.contains("aed")
                && !normalized.contains("안내데스크")
                && !normalized.contains("화장실")
                && !normalized.contains("ladiesroom")
                && !normalized.contains("men'sroom");
    }

    private record ResolvedPlace(
            UUID placeId,
            UUID poiId,
            String externalApiId,
            String name,
            String address,
            Building building,
            Poi selectedPoi,
            boolean isRegistered
    ) {
    }
}
