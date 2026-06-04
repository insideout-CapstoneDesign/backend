package com.insideout.backend.domain.place.service.detail;

import com.insideout.backend.domain.building.entity.Building;
import com.insideout.backend.domain.building.entity.BuildingDirectory;
import com.insideout.backend.domain.building.entity.Floor;
import com.insideout.backend.domain.building.entity.Floorplan;
import com.insideout.backend.domain.building.repository.BuildingDirectoryRepository;
import com.insideout.backend.domain.building.repository.BuildingRepository;
import com.insideout.backend.domain.building.repository.FloorRepository;
import com.insideout.backend.domain.building.repository.FloorplanRepository;
import com.insideout.backend.domain.map.entity.MapVersion;
import com.insideout.backend.domain.map.entity.Poi;
import com.insideout.backend.domain.map.enums.MapType;
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

                Map<UUID, List<Poi>> poisByFloorId = poiRepository.findAllByMapVersionIdWithFloor(publishedMapVersion.getId()).stream()
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
        String selectedName = selectedPoi != null
                ? selectedPoi.getName()
                : buildingDirectory != null ? buildingDirectory.getName() : building.getName();
        String selectedAddress = buildingDirectory != null ? buildingDirectory.getAddress() : building.getAddress();
        String selectedExternalApiId = selectedPoi != null
                ? selectedPoi.getExternalApiId()
                : building.getExternalApiId();
        boolean isRegistered = buildingDirectory != null && buildingDirectory.isPublic();

        return new ResolvedPlace(
                selectedPoi != null ? selectedPoi.getId() : building.getId(),
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

    private record ResolvedPlace(
            UUID placeId,
            String externalApiId,
            String name,
            String address,
            Building building,
            Poi selectedPoi,
            boolean isRegistered
    ) {
    }
}
