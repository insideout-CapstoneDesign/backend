package com.insideout.backend.domain.map.service;

import com.insideout.backend.domain.building.entity.Building;
import com.insideout.backend.domain.building.entity.BuildingEntranceMapping;
import com.insideout.backend.domain.building.entity.Campus;
import com.insideout.backend.domain.building.exception.BuildingErrorCode;
import com.insideout.backend.domain.building.exception.BuildingException;
import com.insideout.backend.domain.building.repository.BuildingEntranceMappingRepository;
import com.insideout.backend.domain.building.repository.BuildingRepository;
import com.insideout.backend.domain.map.dto.request.MapEditorPoiMappingRequestDTO;
import com.insideout.backend.domain.map.dto.request.MapEditorPoiMappingsSaveRequestDTO;
import com.insideout.backend.domain.map.dto.response.MapEditorDraftPoiResponseDTO;
import com.insideout.backend.domain.map.entity.MapVersion;
import com.insideout.backend.domain.map.entity.Poi;
import com.insideout.backend.domain.map.enums.MapType;
import com.insideout.backend.domain.map.exception.MapErrorCode;
import com.insideout.backend.domain.map.exception.MapException;
import com.insideout.backend.domain.map.repository.MapVersionRepository;
import com.insideout.backend.domain.map.repository.PoiRepository;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MapEditorPublishReviewService {

    private final BuildingRepository buildingRepository;
    private final BuildingEntranceMappingRepository buildingEntranceMappingRepository;
    private final MapVersionRepository mapVersionRepository;
    private final PoiRepository poiRepository;
    private final MapEditorPublishFinalizeService mapEditorPublishFinalizeService;

    public List<MapEditorDraftPoiResponseDTO> getBuildingDraftPois(UUID tenantId, UUID buildingId) {
        buildingRepository.findByIdAndTenant_Id(buildingId, tenantId)
                .orElseThrow(() -> new BuildingException(BuildingErrorCode.BUILDING_NOT_FOUND));

        MapVersion draftMapVersion = getDraftMapVersionOrThrow(buildingId);
        List<Poi> pois = poiRepository.findByMapVersionId(draftMapVersion.getId());

        return pois.stream()
                .map(this::toDraftPoiResponse)
                .toList();
    }

    @Transactional
    public void saveBuildingPoiMappings(UUID tenantId, UUID buildingId, MapEditorPoiMappingsSaveRequestDTO request) {
        buildingRepository.findByIdAndTenant_Id(buildingId, tenantId)
                .orElseThrow(() -> new BuildingException(BuildingErrorCode.BUILDING_NOT_FOUND));

        MapVersion draftMapVersion = getDraftMapVersionOrThrow(buildingId);
        GeometryFactory wgsGeometryFactory = new GeometryFactory(new org.locationtech.jts.geom.PrecisionModel(), 4326);

        List<UUID> poiIds = request.mappings().stream()
                .map(MapEditorPoiMappingRequestDTO::poiId)
                .toList();

        List<Poi> pois = poiRepository.findAllById(poiIds);
        Map<UUID, Poi> poiMap = pois.stream()
                .collect(java.util.stream.Collectors.toMap(Poi::getId, poi -> poi));

        for (MapEditorPoiMappingRequestDTO mapping : request.mappings()) {
            // Originally retrieved using: poiRepository.findById(mapping.poiId())
            Poi poi = poiMap.get(mapping.poiId());
            if (poi == null) {
                throw new MapException(MapErrorCode.MAP_VERSION_NOT_FOUND);
            }

            if (!poi.getTenantId().equals(tenantId) || poi.getMapVersion() == null
                    || !poi.getMapVersion().getId().equals(draftMapVersion.getId())) {
                throw new MapException(MapErrorCode.MAP_VERSION_NOT_FOUND);
            }

            if (mapping.excluded()) {
                poi.markExternalMappingExcluded();
            } else if (!StringUtils.hasText(mapping.externalApiId())) {
                poi.updateExternalMapping(null, null, null, null);
            } else {
                Point geomWgs84 = wgsGeometryFactory.createPoint(new Coordinate(mapping.longitude(), mapping.latitude()));
                geomWgs84.setSRID(4326);
                poi.updateExternalMapping(mapping.externalApiId(), geomWgs84, mapping.placeName(), mapping.address());
            }
        }
    }

    public void validatePublishPreconditions(UUID tenantId, Building building, MapVersion draftMapVersion) {
        validateEntranceCalibrationForPublish(tenantId, building, draftMapVersion);
        validatePoiExternalMappingsForPublish(draftMapVersion);
        mapEditorPublishFinalizeService.validateAffineTransform(tenantId, building, draftMapVersion);
    }

    private MapEditorDraftPoiResponseDTO toDraftPoiResponse(Poi poi) {
        return new MapEditorDraftPoiResponseDTO(
                poi.getId(),
                poi.getName(),
                poi.getCode(),
                poi.getFloor() != null ? poi.getFloor().getName() : "-",
                poi.getGeomPx() != null ? poi.getGeomPx().getX() : null,
                poi.getGeomPx() != null ? poi.getGeomPx().getY() : null,
                poi.getExternalApiId(),
                poi.getGeomWgs84() != null ? poi.getGeomWgs84().getY() : null,
                poi.getGeomWgs84() != null ? poi.getGeomWgs84().getX() : null,
                poi.getExternalMappingPlaceName(),
                poi.getExternalMappingAddress(),
                poi.getExternalMappingStatus()
        );
    }

    private MapVersion getDraftMapVersionOrThrow(UUID buildingId) {
        return mapVersionRepository
                .findFirstByBuildingIdAndMapTypeAndStatusOrderByCreatedAtDesc(buildingId, MapType.BUILDING, "draft")
                .orElseThrow(() -> new MapException(MapErrorCode.MAP_VERSION_NOT_FOUND));
    }

    private void validateEntranceCalibrationForPublish(UUID tenantId, Building building, MapVersion draftMapVersion) {
        Campus campus = building.getCampus();
        if (campus == null || campus.getMeta() == null) {
            return;
        }

        Object gatesValue = campus.getMeta().get("gates");
        if (!(gatesValue instanceof List<?> gates) || gates.isEmpty()) {
            return;
        }

        Set<String> expectedGateIds = new LinkedHashSet<>();
        for (Object item : gates) {
            if (!(item instanceof Map<?, ?> gateEntry)) {
                continue;
            }

            Object rawId = gateEntry.get("id");
            if (rawId != null && !String.valueOf(rawId).isBlank()) {
                expectedGateIds.add(String.valueOf(rawId));
            }
        }

        if (expectedGateIds.isEmpty()) {
            return;
        }

        Set<String> mappedGateIds = buildingEntranceMappingRepository
                .findAllByTenantIdAndBuildingIdAndMapVersionIdOrderByCreatedAtAsc(tenantId, building.getId(), draftMapVersion.getId())
                .stream()
                .map(BuildingEntranceMapping::getCampusGateId)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        if (!mappedGateIds.containsAll(expectedGateIds)) {
            throw new MapException(MapErrorCode.PUBLISH_REQUIRES_ENTRANCE_CALIBRATION);
        }
    }

    private void validatePoiExternalMappingsForPublish(MapVersion draftMapVersion) {
        boolean hasPendingPoi = poiRepository.findByMapVersionId(draftMapVersion.getId()).stream()
                .anyMatch(poi -> !"confirmed".equals(poi.getExternalMappingStatus()) && !"excluded".equals(poi.getExternalMappingStatus()));

        if (hasPendingPoi) {
            throw new MapException(MapErrorCode.PUBLISH_REQUIRES_POI_EXTERNAL_MAPPING);
        }
    }
}
