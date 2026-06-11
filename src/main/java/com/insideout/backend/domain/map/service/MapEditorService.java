package com.insideout.backend.domain.map.service;

import com.insideout.backend.domain.ai.entity.AiDetection;
import com.insideout.backend.domain.ai.exception.AiErrorCode;
import com.insideout.backend.domain.ai.exception.AiException;
import com.insideout.backend.domain.ai.repository.AiDetectionRepository;
import com.insideout.backend.domain.building.entity.Building;
import com.insideout.backend.domain.building.entity.Floor;
import com.insideout.backend.domain.building.entity.Floorplan;
import com.insideout.backend.domain.building.exception.BuildingErrorCode;
import com.insideout.backend.domain.building.exception.BuildingException;
import com.insideout.backend.domain.building.repository.BuildingRepository;
import com.insideout.backend.domain.building.repository.FloorRepository;
import com.insideout.backend.domain.building.repository.FloorplanRepository;
import com.insideout.backend.domain.building.entity.FloorplanCalibration;
import com.insideout.backend.domain.map.dto.request.MapEditorVerticalConnectorCreateRequestDTO;
import com.insideout.backend.domain.map.dto.request.MapEditorVerticalConnectorMapRequestDTO;
import com.insideout.backend.domain.map.dto.response.MapEditorVerticalConnectorDTO;
import com.insideout.backend.domain.map.dto.response.MapEditorInitBuildingDraftResponseDTO;
import com.insideout.backend.domain.map.dto.response.MapEditorInitResponseDTO;
import com.insideout.backend.domain.map.dto.response.MapEditorDraftPoiResponseDTO;
import com.insideout.backend.domain.map.dto.request.MapEditorPoiMappingsSaveRequestDTO;
import com.insideout.backend.domain.map.dto.request.MapEditorPoiMappingRequestDTO;
import com.insideout.backend.domain.map.dto.request.MapEditorDraftSaveRequestDTO;
import com.insideout.backend.domain.map.entity.MapVersion;
import com.insideout.backend.domain.map.entity.Poi;
import com.insideout.backend.domain.map.entity.PoiCategory;
import com.insideout.backend.domain.map.entity.ZoneKind;
import com.insideout.backend.domain.map.enums.MapType;
import com.insideout.backend.domain.map.exception.MapErrorCode;
import com.insideout.backend.domain.map.exception.MapException;
import com.insideout.backend.domain.map.repository.MapVersionRepository;
import com.insideout.backend.domain.map.repository.PoiCategoryRepository;
import com.insideout.backend.domain.map.repository.PoiRepository;
import com.insideout.backend.global.infra.storage.service.S3StorageService;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MapEditorService {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new org.locationtech.jts.geom.PrecisionModel(), 0);

    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final FloorplanRepository floorplanRepository;
    private final MapVersionRepository mapVersionRepository;
    private final AiDetectionRepository aiDetectionRepository;
    private final PoiRepository poiRepository;
    private final PoiCategoryRepository poiCategoryRepository;
    private final S3StorageService s3StorageService;
    private final MapEditorDraftVersionService mapEditorDraftVersionService;
    private final MapEditorDraftReadService mapEditorDraftReadService;
    private final MapEditorVerticalConnectorService mapEditorVerticalConnectorService;
    private final MapEditorPublishReviewService mapEditorPublishReviewService;
    private final MapEditorPublishFinalizeService mapEditorPublishFinalizeService;
    private final MapEditorDraftPersistenceService mapEditorDraftPersistenceService;
    private final MapEditorAiDraftInitializationService mapEditorAiDraftInitializationService;

    @Transactional
    public MapEditorInitResponseDTO getOrInitializeFloorDraft(
            UUID tenantId,
            UUID buildingId,
            UUID floorId,
            UUID userId
    ) {
        Building building = buildingRepository.findByIdAndTenant_Id(buildingId, tenantId)
                .orElseThrow(() -> new BuildingException(BuildingErrorCode.BUILDING_NOT_FOUND));
        Floor floor = floorRepository.findByIdAndBuilding_Id(floorId, buildingId)
                .orElseThrow(() -> new BuildingException(BuildingErrorCode.FLOOR_NOT_FOUND));

        Floorplan currentFloorplan = floorplanRepository.findByFloorIdAndIsCurrentTrue(floorId)
                .orElse(null);
        List<AiDetection> aiDetections = currentFloorplan != null
                ? aiDetectionRepository.findByFloorplanIdAndTenantId(currentFloorplan.getId(), tenantId)
                : List.of();

        boolean initializedFromAi = false;
        MapEditorDraftVersionService.DraftMapVersionResult draftResult =
                mapEditorDraftVersionService.getOrCreateBuildingDraftMapVersion(building, userId);
        MapVersion draftMapVersion = draftResult.mapVersion();
        boolean draftCreated = draftResult.created();

        MapEditorDraftReadService.FloorDraftContentState contentState =
                mapEditorDraftReadService.getFloorDraftContentState(draftMapVersion.getId(), floorId);
        if (contentState.hasMissingContent() && !aiDetections.isEmpty()) {
            mapEditorAiDraftInitializationService.initializeFloorDraftFromAi(
                    draftMapVersion,
                    floor,
                    aiDetections,
                    contentState.hasZones(),
                    contentState.hasFloorplanObjects(),
                    contentState.hasEdges(),
                    contentState.hasNodes(),
                    contentState.hasPois()
            );
            initializedFromAi = true;
        }

        String floorplanImageUrl = currentFloorplan != null && currentFloorplan.getImageUrl() != null
                ? s3StorageService.getPresignedUrlFromS3Url(currentFloorplan.getImageUrl())
                : null;

        return mapEditorDraftReadService.buildFloorDraftResponse(
                building,
                floor,
                currentFloorplan,
                floorplanImageUrl,
                draftMapVersion,
                draftCreated,
                initializedFromAi
        );
    }

    @Transactional
    public MapEditorInitResponseDTO saveFloorDraft(
            UUID tenantId,
            UUID buildingId,
            UUID floorId,
            UUID userId,
            MapEditorDraftSaveRequestDTO request
    ) {
        Building building = buildingRepository.findByIdAndTenant_Id(buildingId, tenantId)
                .orElseThrow(() -> new BuildingException(BuildingErrorCode.BUILDING_NOT_FOUND));
        Floor floor = floorRepository.findByIdAndBuilding_Id(floorId, buildingId)
                .orElseThrow(() -> new BuildingException(BuildingErrorCode.FLOOR_NOT_FOUND));

        MapEditorDraftVersionService.DraftMapVersionResult draftResult =
                mapEditorDraftVersionService.getOrCreateBuildingDraftMapVersion(building, userId);
        MapVersion draftMapVersion = draftResult.mapVersion();
        mapEditorDraftPersistenceService.replaceFloorDraftContent(
                tenantId,
                buildingId,
                draftMapVersion,
                floor,
                request
        );

        Floorplan currentFloorplan = floorplanRepository.findByFloorIdAndIsCurrentTrue(floorId)
                .orElse(null);
        String floorplanImageUrl = currentFloorplan != null && currentFloorplan.getImageUrl() != null
                ? s3StorageService.getPresignedUrlFromS3Url(currentFloorplan.getImageUrl())
                : null;

        return mapEditorDraftReadService.buildFloorDraftResponse(
                building,
                floor,
                currentFloorplan,
                floorplanImageUrl,
                draftMapVersion,
                false,
                false
        );
    }

    @Transactional
    public MapEditorInitBuildingDraftResponseDTO initializeBuildingDraft(
            UUID tenantId,
            UUID buildingId,
            UUID userId
    ) {
        Building building = buildingRepository.findByIdAndTenant_Id(buildingId, tenantId)
                .orElseThrow(() -> new BuildingException(BuildingErrorCode.BUILDING_NOT_FOUND));
        List<Floor> floors = floorRepository.findAllByBuilding_IdOrderByLevelDesc(buildingId);

        MapEditorDraftVersionService.DraftMapVersionResult draftResult =
                mapEditorDraftVersionService.getOrCreateBuildingDraftMapVersion(building, userId);
        MapVersion draftMapVersion = draftResult.mapVersion();
        boolean draftCreated = draftResult.created();

        for (Floor floor : floors) {
            Floorplan currentFloorplan = floorplanRepository.findByFloorIdAndIsCurrentTrue(floor.getId()).orElse(null);
            boolean analyzed = currentFloorplan != null
                    && aiDetectionRepository.findAnalyzedFloorplanIdsByTenantIdAndFloorplanIds(
                            tenantId,
                            List.of(currentFloorplan.getId())
                    ).contains(currentFloorplan.getId());
            MapEditorDraftReadService.FloorDraftContentState contentState =
                    mapEditorDraftReadService.getFloorDraftContentState(draftMapVersion.getId(), floor.getId());

            if (contentState.hasMissingContent() && analyzed && currentFloorplan != null) {
                List<AiDetection> aiDetections = aiDetectionRepository.findByFloorplanIdAndTenantId(currentFloorplan.getId(), tenantId);
                if (!aiDetections.isEmpty()) {
                    mapEditorAiDraftInitializationService.initializeFloorDraftFromAi(
                            draftMapVersion,
                            floor,
                            aiDetections,
                            contentState.hasZones(),
                            contentState.hasFloorplanObjects(),
                            contentState.hasEdges(),
                            contentState.hasNodes(),
                            contentState.hasPois()
                    );
                    contentState = mapEditorDraftReadService.getFloorDraftContentState(draftMapVersion.getId(), floor.getId());
                }
            }
        }

        return mapEditorDraftReadService.buildBuildingDraftResponse(
                tenantId,
                building,
                floors,
                draftMapVersion,
                draftCreated
        );
    }

    @Transactional(readOnly = true)
    public List<MapEditorVerticalConnectorDTO> getVerticalConnectors(UUID tenantId, UUID buildingId, UUID userId) {
        return mapEditorVerticalConnectorService.getVerticalConnectors(tenantId, buildingId);
    }

    @Transactional
    public MapEditorVerticalConnectorDTO createVerticalConnector(
            UUID tenantId,
            UUID buildingId,
            UUID userId,
            MapEditorVerticalConnectorCreateRequestDTO request
    ) {
        return mapEditorVerticalConnectorService.createVerticalConnector(tenantId, buildingId, userId, request);
    }

    @Transactional
    public void deleteVerticalConnector(UUID tenantId, UUID buildingId, UUID connectorId, UUID userId) {
        mapEditorVerticalConnectorService.deleteVerticalConnector(tenantId, buildingId, connectorId);
    }

    @Transactional
    public MapEditorVerticalConnectorDTO updateVerticalConnector(
            UUID tenantId,
            UUID buildingId,
            UUID connectorId,
            UUID userId,
            com.insideout.backend.domain.map.dto.request.MapEditorVerticalConnectorUpdateRequestDTO request
    ) {
        return mapEditorVerticalConnectorService.updateVerticalConnector(tenantId, buildingId, connectorId, request);
    }

    @Transactional
    public MapEditorVerticalConnectorDTO mapVerticalConnectorNode(
            UUID tenantId,
            UUID buildingId,
            UUID connectorId,
            UUID userId,
            MapEditorVerticalConnectorMapRequestDTO request
    ) {
        return mapEditorVerticalConnectorService.mapVerticalConnectorNode(
                tenantId,
                buildingId,
                connectorId,
                request.floorId(),
                request
        );
    }

    @Transactional
    public MapEditorVerticalConnectorDTO unmapVerticalConnectorNode(
            UUID tenantId,
            UUID buildingId,
            UUID connectorId,
            UUID floorId,
            UUID userId
    ) {
        return mapEditorVerticalConnectorService.unmapVerticalConnectorNode(tenantId, buildingId, connectorId, floorId);
    }

    @Transactional
    public MapEditorInitBuildingDraftResponseDTO publishBuildingDraft(
            UUID tenantId,
            UUID buildingId,
            UUID userId
    ) {
        Building building = buildingRepository.findByIdAndTenant_Id(buildingId, tenantId)
                .orElseThrow(() -> new BuildingException(BuildingErrorCode.BUILDING_NOT_FOUND));
        List<Floor> floors = floorRepository.findAllByBuilding_IdOrderByLevelDesc(buildingId);

        MapVersion draftMapVersion = mapVersionRepository
                .findFirstByBuildingIdAndMapTypeAndStatusOrderByCreatedAtDesc(buildingId, MapType.BUILDING, "draft")
                .orElseThrow(() -> new com.insideout.backend.domain.map.exception.MapException(
                        com.insideout.backend.domain.map.exception.MapErrorCode.MAP_VERSION_NOT_FOUND
                ));

        mapEditorPublishReviewService.validatePublishPreconditions(tenantId, building, draftMapVersion);
        mapEditorPublishFinalizeService.finalizePublishedBuildingDraft(tenantId, building, floors, draftMapVersion);

        return mapEditorDraftReadService.buildBuildingDraftResponse(
                tenantId,
                building,
                floors,
                draftMapVersion,
                false
        );
    }

    public List<MapEditorDraftPoiResponseDTO> getBuildingDraftPois(UUID tenantId, UUID buildingId) {
        return mapEditorPublishReviewService.getBuildingDraftPois(tenantId, buildingId);
    }

    @Transactional
    public void saveBuildingPoiMappings(UUID tenantId, UUID buildingId, MapEditorPoiMappingsSaveRequestDTO request) {
        mapEditorPublishReviewService.saveBuildingPoiMappings(tenantId, buildingId, request);
    }
}
