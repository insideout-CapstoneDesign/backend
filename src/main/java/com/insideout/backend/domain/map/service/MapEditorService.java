package com.insideout.backend.domain.map.service;

import com.insideout.backend.domain.ai.dto.response.DetectionViewDTO;
import com.insideout.backend.domain.ai.entity.AiDetection;
import com.insideout.backend.domain.ai.exception.AiErrorCode;
import com.insideout.backend.domain.ai.exception.AiException;
import com.insideout.backend.domain.ai.repository.AiDetectionRepository;
import com.insideout.backend.domain.building.entity.Building;
import com.insideout.backend.domain.building.entity.BuildingEntranceMapping;
import com.insideout.backend.domain.building.entity.Floor;
import com.insideout.backend.domain.building.entity.Floorplan;
import com.insideout.backend.domain.building.exception.BuildingErrorCode;
import com.insideout.backend.domain.building.exception.BuildingException;
import com.insideout.backend.domain.building.repository.BuildingEntranceMappingRepository;
import com.insideout.backend.domain.building.repository.BuildingRepository;
import com.insideout.backend.domain.building.repository.FloorRepository;
import com.insideout.backend.domain.building.repository.FloorplanRepository;
import com.insideout.backend.domain.building.entity.Campus;
import com.insideout.backend.domain.building.entity.FloorplanCalibration;
import com.insideout.backend.domain.building.repository.FloorplanCalibrationRepository;
import com.insideout.backend.domain.map.entity.VerticalConnector;
import com.insideout.backend.domain.map.entity.VerticalConnectorNode;
import com.insideout.backend.domain.map.repository.VerticalConnectorRepository;
import com.insideout.backend.domain.map.repository.VerticalConnectorNodeRepository;
import com.insideout.backend.domain.map.dto.request.MapEditorVerticalConnectorCreateRequestDTO;
import com.insideout.backend.domain.map.dto.request.MapEditorVerticalConnectorMapRequestDTO;
import com.insideout.backend.domain.map.dto.response.MapEditorVerticalConnectorDTO;
import com.insideout.backend.domain.map.dto.response.MapEditorVerticalConnectorNodeDTO;
import com.insideout.backend.domain.map.dto.response.MapEditorEdgeDTO;
import com.insideout.backend.domain.map.dto.response.MapEditorFloorplanObjectDTO;
import com.insideout.backend.domain.map.dto.response.MapEditorInitBuildingDraftFloorDTO;
import com.insideout.backend.domain.map.dto.response.MapEditorInitBuildingDraftResponseDTO;
import com.insideout.backend.domain.map.dto.response.MapEditorInitResponseDTO;
import com.insideout.backend.domain.map.dto.response.MapEditorNodeDTO;
import com.insideout.backend.domain.map.dto.response.MapEditorPoiDTO;
import com.insideout.backend.domain.map.dto.response.MapEditorZoneDTO;
import com.insideout.backend.domain.map.dto.response.MapEditorDraftPoiResponseDTO;
import com.insideout.backend.domain.map.dto.request.MapEditorPoiMappingsSaveRequestDTO;
import com.insideout.backend.domain.map.dto.request.MapEditorPoiMappingRequestDTO;
import com.insideout.backend.domain.map.dto.request.MapEditorDraftEdgeSaveDTO;
import com.insideout.backend.domain.map.dto.request.MapEditorDraftNodeSaveDTO;
import com.insideout.backend.domain.map.dto.request.MapEditorDraftPoiSaveDTO;
import com.insideout.backend.domain.map.dto.request.MapEditorDraftSaveRequestDTO;
import com.insideout.backend.domain.map.dto.request.MapEditorDraftZoneSaveDTO;
import com.insideout.backend.domain.map.entity.Edge;
import com.insideout.backend.domain.map.entity.FloorplanObject;
import com.insideout.backend.domain.map.entity.MapVersion;
import com.insideout.backend.domain.map.entity.Node;
import com.insideout.backend.domain.map.entity.Poi;
import com.insideout.backend.domain.map.entity.PoiCategory;
import com.insideout.backend.domain.map.entity.Zone;
import com.insideout.backend.domain.map.entity.ZoneKind;
import com.insideout.backend.domain.map.enums.MapType;
import com.insideout.backend.domain.map.exception.MapErrorCode;
import com.insideout.backend.domain.map.exception.MapException;
import com.insideout.backend.domain.map.repository.EdgeRepository;
import com.insideout.backend.domain.map.repository.FloorplanObjectRepository;
import com.insideout.backend.domain.map.repository.MapVersionRepository;
import com.insideout.backend.domain.map.repository.NodeRepository;
import com.insideout.backend.domain.map.repository.PoiCategoryRepository;
import com.insideout.backend.domain.map.repository.PoiRepository;
import com.insideout.backend.domain.map.repository.ZoneRepository;
import com.insideout.backend.domain.user.entity.User;
import com.insideout.backend.domain.user.repository.UserRepository;
import com.insideout.backend.global.infra.storage.service.S3StorageService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.RoundingMode;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MapEditorService {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new org.locationtech.jts.geom.PrecisionModel(), 0);

    private final BuildingRepository buildingRepository;
    private final BuildingEntranceMappingRepository buildingEntranceMappingRepository;
    private final FloorRepository floorRepository;
    private final FloorplanRepository floorplanRepository;
    private final MapVersionRepository mapVersionRepository;
    private final AiDetectionRepository aiDetectionRepository;
    private final NodeRepository nodeRepository;
    private final EdgeRepository edgeRepository;
    private final PoiRepository poiRepository;
    private final ZoneRepository zoneRepository;
    private final FloorplanObjectRepository floorplanObjectRepository;
    private final PoiCategoryRepository poiCategoryRepository;
    private final UserRepository userRepository;
    private final S3StorageService s3StorageService;
    private final VerticalConnectorRepository verticalConnectorRepository;
    private final VerticalConnectorNodeRepository verticalConnectorNodeRepository;
    private final FloorplanCalibrationRepository floorplanCalibrationRepository;
    @PersistenceContext
    private final EntityManager entityManager;

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
        DraftMapVersionResult draftResult = getOrCreateBuildingDraftMapVersion(building, userId);
        MapVersion draftMapVersion = draftResult.mapVersion();
        boolean draftCreated = draftResult.created();

        FloorDraftContentState contentState = getFloorDraftContentState(draftMapVersion.getId(), floorId);
        if (contentState.hasMissingContent() && !aiDetections.isEmpty()) {
            initializeFloorDraftFromAi(draftMapVersion, floor, aiDetections, contentState);
            initializedFromAi = true;
        }

        String floorplanImageUrl = currentFloorplan != null && currentFloorplan.getImageUrl() != null
                ? s3StorageService.getPresignedUrlFromS3Url(currentFloorplan.getImageUrl())
                : null;

        return buildFloorDraftResponse(
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

        DraftMapVersionResult draftResult = getOrCreateBuildingDraftMapVersion(building, userId);
        MapVersion draftMapVersion = draftResult.mapVersion();
        List<UUID> existingFloorNodeIds = nodeRepository.findByMapVersionIdAndFloorId(draftMapVersion.getId(), floorId).stream()
                .map(Node::getId)
                .toList();
        Map<UUID, Poi> existingPoisById = poiRepository.findByMapVersionIdAndFloorId(draftMapVersion.getId(), floorId).stream()
                .collect(java.util.stream.Collectors.toMap(
                        Poi::getId,
                        java.util.function.Function.identity(),
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ));

        List<BuildingEntranceMapping> floorMappings = buildingEntranceMappingRepository
                .findAllByTenantIdAndBuildingIdOrderByCreatedAtAsc(tenantId, buildingId).stream()
                .filter(m -> existingFloorNodeIds.contains(m.getEntranceNodeId()))
                .toList();

        List<VerticalConnectorNode> floorConnectorNodes = existingFloorNodeIds.isEmpty()
                ? List.of()
                : verticalConnectorNodeRepository.findByNodeIdIn(existingFloorNodeIds);

        if (!floorMappings.isEmpty()) {
            buildingEntranceMappingRepository.deleteAll(floorMappings);
            buildingEntranceMappingRepository.flush();
        }

        if (!floorConnectorNodes.isEmpty()) {
            verticalConnectorNodeRepository.deleteAll(floorConnectorNodes);
            verticalConnectorNodeRepository.flush();
        }

        edgeRepository.deleteByMapVersionIdAndFloorId(draftMapVersion.getId(), floorId);
        poiRepository.deleteByMapVersionIdAndFloorId(draftMapVersion.getId(), floorId);
        zoneRepository.deleteByMapVersionIdAndFloorId(draftMapVersion.getId(), floorId);
        nodeRepository.deleteByMapVersionIdAndFloorId(draftMapVersion.getId(), floorId);

        Map<UUID, Node> savedNodesByRequestId = saveDraftNodes(draftMapVersion, floor, request.nodes());
        saveDraftZones(draftMapVersion, floor, request.zones());
        saveDraftPois(draftMapVersion, floor, request.pois(), savedNodesByRequestId, existingPoisById);
        saveDraftEdges(draftMapVersion, request.edges(), savedNodesByRequestId);
        syncEntranceMappingsAfterFloorDraftSave(savedNodesByRequestId, floorMappings);
        syncVerticalConnectorNodesAfterFloorDraftSave(savedNodesByRequestId, floorConnectorNodes);

        Floorplan currentFloorplan = floorplanRepository.findByFloorIdAndIsCurrentTrue(floorId)
                .orElse(null);
        String floorplanImageUrl = currentFloorplan != null && currentFloorplan.getImageUrl() != null
                ? s3StorageService.getPresignedUrlFromS3Url(currentFloorplan.getImageUrl())
                : null;

        return buildFloorDraftResponse(
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

        DraftMapVersionResult draftResult = getOrCreateBuildingDraftMapVersion(building, userId);
        MapVersion draftMapVersion = draftResult.mapVersion();
        boolean draftCreated = draftResult.created();

        List<UUID> floorIds = floors.stream().map(Floor::getId).toList();
        Map<UUID, Floorplan> currentFloorplansByFloorId = floorIds.isEmpty()
                ? Map.of()
                : floorplanRepository.findAllByFloorIdInAndIsCurrentTrue(floorIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        floorplan -> floorplan.getFloor().getId(),
                        floorplan -> floorplan,
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ));

        List<UUID> currentFloorplanIds = currentFloorplansByFloorId.values().stream()
                .map(Floorplan::getId)
                .toList();
        Set<UUID> analyzedFloorplanIds = currentFloorplanIds.isEmpty()
                ? Set.of()
                : new LinkedHashSet<>(aiDetectionRepository.findAnalyzedFloorplanIdsByTenantIdAndFloorplanIds(tenantId, currentFloorplanIds));

        List<MapEditorInitBuildingDraftFloorDTO> floorStates = new ArrayList<>();
        int analyzedFloorCount = 0;
        int draftReadyFloorCount = 0;

        for (Floor floor : floors) {
            Floorplan currentFloorplan = currentFloorplansByFloorId.get(floor.getId());
            boolean analyzed = currentFloorplan != null && analyzedFloorplanIds.contains(currentFloorplan.getId());
            FloorDraftContentState contentState = getFloorDraftContentState(draftMapVersion.getId(), floor.getId());
            boolean hasDraftContent = contentState.hasAnyContent();
            boolean initializedFromAi = false;

            if (analyzed) {
                analyzedFloorCount++;
            }

            if (contentState.hasMissingContent() && analyzed && currentFloorplan != null) {
                List<AiDetection> aiDetections = aiDetectionRepository.findByFloorplanIdAndTenantId(currentFloorplan.getId(), tenantId);
                if (!aiDetections.isEmpty()) {
                    initializeFloorDraftFromAi(draftMapVersion, floor, aiDetections, contentState);
                    contentState = getFloorDraftContentState(draftMapVersion.getId(), floor.getId());
                    hasDraftContent = contentState.hasAnyContent();
                    initializedFromAi = true;
                }
            }

            if (contentState.isFullyReady()) {
                draftReadyFloorCount++;
            }

            floorStates.add(MapEditorInitBuildingDraftFloorDTO.of(
                floor,
                currentFloorplan,
                analyzed,
                contentState.isFullyReady(),
                initializedFromAi
            ));
        }

        return MapEditorInitBuildingDraftResponseDTO.of(
                building,
                draftMapVersion,
                draftCreated,
                analyzedFloorCount,
                draftReadyFloorCount,
                floorStates
        );
    }

    private MapEditorInitResponseDTO buildFloorDraftResponse(
            Building building,
            Floor floor,
            Floorplan currentFloorplan,
            String floorplanImageUrl,
            MapVersion draftMapVersion,
            boolean draftCreated,
            boolean initializedFromAi
    ) {
        List<AiDetection> aiDetections = currentFloorplan != null
                ? aiDetectionRepository.findByFloorplanIdAndTenantId(currentFloorplan.getId(), building.getTenant().getId())
                : List.of();

        return MapEditorInitResponseDTO.of(
                building,
                floor,
                currentFloorplan,
                floorplanImageUrl,
                draftMapVersion,
                draftCreated,
                initializedFromAi,
                aiDetections.stream().map(DetectionViewDTO::from).toList(),
                nodeRepository.findByMapVersionIdAndFloorId(draftMapVersion.getId(), floor.getId()).stream()
                        .map(MapEditorNodeDTO::from)
                        .toList(),
                edgeRepository.findByMapVersionIdAndFloorId(draftMapVersion.getId(), floor.getId()).stream()
                        .map(MapEditorEdgeDTO::from)
                        .toList(),
                poiRepository.findByMapVersionIdAndFloorId(draftMapVersion.getId(), floor.getId()).stream()
                        .map(MapEditorPoiDTO::from)
                        .toList(),
                zoneRepository.findByMapVersionIdAndFloorId(draftMapVersion.getId(), floor.getId()).stream()
                        .map(MapEditorZoneDTO::from)
                        .toList(),
                floorplanObjectRepository.findByMapVersionIdAndFloorId(draftMapVersion.getId(), floor.getId()).stream()
                        .map(MapEditorFloorplanObjectDTO::from)
                        .toList()
        );
    }

    private FloorDraftContentState getFloorDraftContentState(UUID mapVersionId, UUID floorId) {
        return new FloorDraftContentState(
                !zoneRepository.findByMapVersionIdAndFloorId(mapVersionId, floorId).isEmpty(),
                !floorplanObjectRepository.findByMapVersionIdAndFloorId(mapVersionId, floorId).isEmpty(),
                !edgeRepository.findByMapVersionIdAndFloorId(mapVersionId, floorId).isEmpty(),
                !nodeRepository.findByMapVersionIdAndFloorId(mapVersionId, floorId).isEmpty(),
                !poiRepository.findByMapVersionIdAndFloorId(mapVersionId, floorId).isEmpty()
        );
    }

    private DraftMapVersionResult getOrCreateBuildingDraftMapVersion(Building building, UUID userId) {
        return mapVersionRepository
                .findFirstByBuildingIdAndMapTypeAndStatusOrderByCreatedAtDesc(building.getId(), MapType.BUILDING, "draft")
                .map(mapVersion -> new DraftMapVersionResult(mapVersion, false))
                .orElseGet(() -> createOrReuseBuildingDraftMapVersion(building, userId));
    }

    private DraftMapVersionResult createOrReuseBuildingDraftMapVersion(Building building, UUID userId) {
        try {
            return new DraftMapVersionResult(createDraftMapVersion(building, userId), true);
        } catch (DataIntegrityViolationException exception) {
            return mapVersionRepository
                    .findFirstByBuildingIdAndMapTypeAndStatusOrderByCreatedAtDesc(building.getId(), MapType.BUILDING, "draft")
                    .map(mapVersion -> new DraftMapVersionResult(mapVersion, false))
                    .orElseThrow(() -> exception);
        }
    }

    private MapVersion createDraftMapVersion(Building building, UUID userId) {
        User createdBy = userId != null ? userRepository.findById(userId).orElse(null) : null;
        Optional<MapVersion> latestPublishedVersion = mapVersionRepository
                .findFirstByBuildingIdAndMapTypeAndStatusOrderByCreatedAtDesc(
                        building.getId(),
                        MapType.BUILDING,
                        "published"
                );
        UUID parentVersionId = latestPublishedVersion
                .map(MapVersion::getId)
                .orElse(null);

        MapVersion draftMapVersion = mapVersionRepository.save(
                MapVersion.builder()
                        .tenantId(building.getTenant().getId())
                        .building(building)
                        .mapType(MapType.BUILDING)
                        .label(building.getName() + " draft " + OffsetDateTime.now())
                        .status("draft")
                        .parentVersionId(parentVersionId)
                        .createdBy(createdBy)
                        .build()
        );

        latestPublishedVersion.ifPresent(published -> clonePublishedMapVersionIntoDraft(building, published, draftMapVersion));
        return draftMapVersion;
    }

    private void clonePublishedMapVersionIntoDraft(
            Building building,
            MapVersion publishedMapVersion,
            MapVersion draftMapVersion
    ) {
        List<Floor> floors = floorRepository.findAllByBuilding_IdOrderByLevelDesc(building.getId());

        Map<UUID, Node> clonedNodesBySourceId = new LinkedHashMap<>();
        List<Node> sourceNodes = nodeRepository.findByMapVersionId(publishedMapVersion.getId());
        if (!sourceNodes.isEmpty()) {
            List<Node> savedNodes = nodeRepository.saveAll(sourceNodes.stream()
                    .map(node -> Node.builder()
                            .tenantId(draftMapVersion.getTenantId())
                            .mapVersion(draftMapVersion)
                            .floor(node.getFloor())
                            .kindCode(node.getKindCode())
                            .geomPx(node.getGeomPx())
                            .geomWgs84(node.getGeomWgs84())
                            .nameKo(node.getNameKo())
                            .properties(node.getProperties())
                            .source(node.getSource())
                            .aiDetectionId(node.getAiDetectionId())
                            .build())
                    .toList());

            for (int index = 0; index < sourceNodes.size(); index++) {
                clonedNodesBySourceId.put(sourceNodes.get(index).getId(), savedNodes.get(index));
            }
        }

        List<Zone> zonesToClone = new ArrayList<>();
        List<FloorplanObject> floorplanObjectsToClone = new ArrayList<>();
        for (Floor floor : floors) {
            zonesToClone.addAll(zoneRepository.findByMapVersionIdAndFloorId(publishedMapVersion.getId(), floor.getId()));
            floorplanObjectsToClone.addAll(floorplanObjectRepository.findByMapVersionIdAndFloorId(publishedMapVersion.getId(), floor.getId()));
        }

        if (!zonesToClone.isEmpty()) {
            zoneRepository.saveAll(zonesToClone.stream()
                    .map(zone -> Zone.builder()
                            .tenantId(draftMapVersion.getTenantId())
                            .mapVersion(draftMapVersion)
                            .floor(zone.getFloor())
                            .kind(zone.getKind())
                            .name(zone.getName())
                            .geomPx(zone.getGeomPx())
                            .properties(zone.getProperties())
                            .build())
                    .toList());
        }

        if (!floorplanObjectsToClone.isEmpty()) {
            floorplanObjectRepository.saveAll(floorplanObjectsToClone.stream()
                    .map(object -> FloorplanObject.builder()
                            .tenantId(draftMapVersion.getTenantId())
                            .mapVersion(draftMapVersion)
                            .floor(object.getFloor())
                            .kind(object.getKind())
                            .geomPx(object.getGeomPx())
                            .properties(object.getProperties())
                            .source(object.getSource())
                            .aiDetectionId(object.getAiDetectionId())
                            .build())
                    .toList());
        }

        Map<UUID, Poi> clonedPoisBySourceId = new LinkedHashMap<>();
        List<Poi> sourcePois = poiRepository.findByMapVersionId(publishedMapVersion.getId());
        if (!sourcePois.isEmpty()) {
            List<Poi> savedPois = poiRepository.saveAll(sourcePois.stream()
                    .map(poi -> Poi.builder()
                            .tenantId(draftMapVersion.getTenantId())
                            .mapVersion(draftMapVersion)
                            .floor(poi.getFloor())
                            .categoryId(poi.getCategoryId())
                            .name(poi.getName())
                            .code(poi.getCode())
                            .geomPx(poi.getGeomPx())
                            .footprintPx(poi.getFootprintPx())
                            .geomWgs84(poi.getGeomWgs84())
                            .anchorNodeId(Optional.ofNullable(poi.getAnchorNodeId()).map(clonedNodesBySourceId::get).map(Node::getId).orElse(null))
                            .tags(poi.getTags())
                            .attrs(poi.getAttrs())
                            .externalApiId(poi.getExternalApiId())
                            .source(poi.getSource())
                            .aiDetectionId(poi.getAiDetectionId())
                            .build())
                    .toList());

            for (int index = 0; index < sourcePois.size(); index++) {
                clonedPoisBySourceId.put(sourcePois.get(index).getId(), savedPois.get(index));
            }
        }

        List<Edge> sourceEdges = edgeRepository.findByMapVersionId(publishedMapVersion.getId());
        if (!sourceEdges.isEmpty()) {
            edgeRepository.saveAll(sourceEdges.stream()
                    .map(edge -> Edge.builder()
                            .tenantId(draftMapVersion.getTenantId())
                            .mapVersion(draftMapVersion)
                            .fromNode(clonedNodesBySourceId.get(edge.getFromNode().getId()))
                            .toNode(clonedNodesBySourceId.get(edge.getToNode().getId()))
                            .kindCode(edge.getKindCode())
                            .geomPx(edge.getGeomPx())
                            .geomWgs84(edge.getGeomWgs84())
                            .lengthM(edge.getLengthM())
                            .isDirected(edge.isDirected())
                            .baseWeight(edge.getBaseWeight())
                            .properties(edge.getProperties())
                            .source(edge.getSource())
                            .aiDetectionId(edge.getAiDetectionId())
                            .build())
                    .toList());
        }

        Map<UUID, VerticalConnector> clonedConnectorsBySourceId = new LinkedHashMap<>();
        List<VerticalConnector> sourceConnectors = verticalConnectorRepository.findByMapVersionId(publishedMapVersion.getId());
        if (!sourceConnectors.isEmpty()) {
            List<VerticalConnector> savedConnectors = verticalConnectorRepository.saveAll(sourceConnectors.stream()
                    .map(connector -> VerticalConnector.builder()
                            .tenantId(draftMapVersion.getTenantId())
                            .mapVersion(draftMapVersion)
                            .building(building)
                            .kind(connector.getKind())
                            .name(connector.getName())
                            .capacity(connector.getCapacity())
                            .avgWaitSeconds(connector.getAvgWaitSeconds())
                            .direction(connector.getDirection())
                            .accessibility(connector.getAccessibility())
                            .build())
                    .toList());

            for (int index = 0; index < sourceConnectors.size(); index++) {
                clonedConnectorsBySourceId.put(sourceConnectors.get(index).getId(), savedConnectors.get(index));
            }
        }

        List<VerticalConnectorNode> sourceConnectorNodes = verticalConnectorNodeRepository.findByConnectorMapVersionId(publishedMapVersion.getId());
        if (!sourceConnectorNodes.isEmpty()) {
            verticalConnectorNodeRepository.saveAll(sourceConnectorNodes.stream()
                    .map(node -> VerticalConnectorNode.builder()
                            .tenantId(draftMapVersion.getTenantId())
                            .connector(clonedConnectorsBySourceId.get(node.getConnector().getId()))
                            .node(clonedNodesBySourceId.get(node.getNode().getId()))
                            .floor(node.getFloor())
                            .build())
                    .filter(connectorNode -> connectorNode.getConnector() != null && connectorNode.getNode() != null)
                    .toList());
        }

        List<BuildingEntranceMapping> mappings = buildingEntranceMappingRepository
                .findAllByTenantIdAndBuildingIdOrderByCreatedAtAsc(building.getTenant().getId(), building.getId());
        if (!mappings.isEmpty()) {
            for (BuildingEntranceMapping mapping : mappings) {
                UUID newEntranceNodeId = mapping.getEntranceNodeId();
                if (clonedNodesBySourceId.containsKey(newEntranceNodeId)) {
                    newEntranceNodeId = clonedNodesBySourceId.get(newEntranceNodeId).getId();
                }
                UUID newEntrancePoiId = mapping.getEntrancePoiId();
                if (newEntrancePoiId != null && clonedPoisBySourceId.containsKey(newEntrancePoiId)) {
                    newEntrancePoiId = clonedPoisBySourceId.get(newEntrancePoiId).getId();
                }
                mapping.updateEntrance(newEntranceNodeId, newEntrancePoiId);
            }
            buildingEntranceMappingRepository.saveAll(mappings);
        }
    }

    private record DraftMapVersionResult(MapVersion mapVersion, boolean created) {
    }

    private void initializeFloorDraftFromAi(
            MapVersion draftMapVersion,
            Floor floor,
            List<AiDetection> detections,
            FloorDraftContentState contentState
    ) {
        Map<String, Long> poiCategoryIdsByCode = loadPoiCategoryIds(detections);

        List<Zone> zonesToSave = new ArrayList<>();
        List<FloorplanObject> floorplanObjectsToSave = new ArrayList<>();
        List<Node> explicitNodesToSave = new ArrayList<>();
        List<Poi> poisToSave = new ArrayList<>();
        List<AiDetection> edgeDetections = new ArrayList<>();
        List<AiDetection> textDetections = detections.stream()
                .filter(detection -> "text".equals(normalizeDetectType(detection.getDetectType())))
                .filter(detection -> extractReadableText(detection) != null)
                .toList();

        Map<String, Node> nodeCache = new LinkedHashMap<>();
        if (contentState.hasNodes() || contentState.hasEdges()) {
            nodeRepository.findByMapVersionIdAndFloorId(draftMapVersion.getId(), floor.getId()).forEach(
                    node -> nodeCache.put(toNodeCacheKey(node.getGeomPx()), node)
            );
        }

        for (AiDetection detection : detections) {
            String detectType = normalizeDetectType(detection.getDetectType());
            switch (detectType) {
                case "room" -> {
                    if (!contentState.hasZones()) {
                        buildZone(draftMapVersion, floor, detection, ZoneKind.room)
                                .map(zone -> enrichZoneNameFromText(zone, textDetections))
                                .ifPresent(zonesToSave::add);
                    }
                }
                case "corridor", "walkable_area" -> {
                    if (!contentState.hasZones()) {
                        buildZone(draftMapVersion, floor, detection, ZoneKind.corridor)
                                .map(zone -> enrichZoneNameFromText(zone, textDetections))
                                .ifPresent(zonesToSave::add);
                    }
                }
                case "blocked_area" -> {
                    if (!contentState.hasZones()) {
                        buildZone(draftMapVersion, floor, detection, ZoneKind.restricted)
                                .map(zone -> enrichZoneNameFromText(zone, textDetections))
                                .ifPresent(zonesToSave::add);
                    }
                }
                case "wall" -> {
                    if (!contentState.hasFloorplanObjects()) {
                        buildFloorplanObject(draftMapVersion, floor, detection, "wall")
                                .ifPresent(floorplanObjectsToSave::add);
                    }
                }
                case "door" -> {
                    if (!contentState.hasFloorplanObjects()) {
                        buildFloorplanObject(draftMapVersion, floor, detection, "door")
                                .ifPresent(floorplanObjectsToSave::add);
                    }
                }
                case "node", "node_candidate", "entrance", "stair", "elevator", "escalator",
                     "center_junction", "center_endpoint" -> {
                    if (contentState.hasNodes()) {
                        if (isVerticalFacilityDetectType(detectType) && !contentState.hasPois()) {
                            buildPoi(draftMapVersion, floor, detection, poiCategoryIdsByCode)
                                    .map(poi -> enrichPoiNameFromText(poi, detection, textDetections))
                                    .ifPresent(poisToSave::add);
                        }
                        continue;
                    }
                    Point point = toPoint(detection.getGeomPx());
                    if (point == null) {
                        continue;
                    }
                    String key = toNodeCacheKey(point);
                    if (nodeCache.containsKey(key)) {
                        continue;
                    }
                    Node node = Node.builder()
                            .tenantId(draftMapVersion.getTenantId())
                            .mapVersion(draftMapVersion)
                            .floor(floor)
                            .kindCode(resolveNodeKindCode(detectType))
                            .geomPx(point)
                            .nameKo(resolveDetectionName(detection))
                            .properties(buildDetectionProperties(detection))
                            .source("ai")
                            .aiDetectionId(detection.getId())
                            .build();
                    explicitNodesToSave.add(node);
                    nodeCache.put(key, node);

                    if (isVerticalFacilityDetectType(detectType) && !contentState.hasPois()) {
                        buildPoi(draftMapVersion, floor, detection, poiCategoryIdsByCode)
                                .map(poi -> enrichPoiNameFromText(poi, detection, textDetections))
                                .ifPresent(poisToSave::add);
                    }
                }
                case "edge", "edge_candidate" -> {
                    if (!contentState.hasEdges()) {
                        edgeDetections.add(detection);
                    }
                }
                default -> buildPoi(draftMapVersion, floor, detection, poiCategoryIdsByCode)
                        .map(poi -> enrichPoiNameFromText(poi, detection, textDetections))
                        .ifPresent(poi -> {
                            if (!contentState.hasPois()) {
                                poisToSave.add(poi);
                            }
                        });
            }
        }

        if (!zonesToSave.isEmpty()) {
            List<Zone> savedZones = zoneRepository.saveAll(suppressDuplicateRoomZones(zonesToSave));
            savedZones.forEach(zone -> detections.stream()
                    .filter(detection -> Objects.equals(detection.getId(), extractAiDetectionId(zone)))
                    .findFirst()
                    .ifPresent(detection -> detection.markCommitted("zone", zone.getId())));
        }

        if (!floorplanObjectsToSave.isEmpty()) {
            List<FloorplanObject> savedObjects = floorplanObjectRepository.saveAll(floorplanObjectsToSave);
            savedObjects.forEach(object -> detections.stream()
                    .filter(detection -> Objects.equals(detection.getId(), object.getAiDetectionId()))
                    .findFirst()
                    .ifPresent(detection -> detection.markCommitted("floorplan_object", object.getId())));
        }

        if (!explicitNodesToSave.isEmpty()) {
            List<Node> savedNodes = nodeRepository.saveAll(explicitNodesToSave);
            nodeCache.clear();
            for (Node savedNode : savedNodes) {
                nodeCache.put(toNodeCacheKey(savedNode.getGeomPx()), savedNode);
                detections.stream()
                        .filter(detection -> Objects.equals(detection.getId(), savedNode.getAiDetectionId()))
                        .findFirst()
                        .ifPresent(detection -> detection.markCommitted("node", savedNode.getId()));
            }

            for (Poi poi : poisToSave) {
                if (poi.getAiDetectionId() != null) {
                    savedNodes.stream()
                            .filter(n -> Objects.equals(n.getAiDetectionId(), poi.getAiDetectionId()))
                            .findFirst()
                            .ifPresent(n -> poi.updateAnchorNodeId(n.getId()));
                }
            }
        }

        if (!poisToSave.isEmpty()) {
            List<Poi> savedPois = poiRepository.saveAll(suppressGenericPoiCandidatesNearFacilities(poisToSave));
            savedPois.forEach(poi -> detections.stream()
                    .filter(detection -> Objects.equals(detection.getId(), poi.getAiDetectionId()))
                    .findFirst()
                    .ifPresent(detection -> detection.markCommitted("poi", poi.getId())));
        }

        saveEdges(draftMapVersion, floor, edgeDetections, nodeCache);
    }

    private Map<UUID, Node> saveDraftNodes(
            MapVersion draftMapVersion,
            Floor floor,
            List<MapEditorDraftNodeSaveDTO> nodes
    ) {
        if (nodes == null || nodes.isEmpty()) {
            return Map.of();
        }

        List<Node> nodesToSave = new ArrayList<>();
        List<UUID> requestIds = new ArrayList<>();

        for (MapEditorDraftNodeSaveDTO node : nodes) {
            Point point = toPoint(node.geomPx());
            if (point == null) {
                continue;
            }

            nodesToSave.add(
                    Node.builder()
                            .tenantId(draftMapVersion.getTenantId())
                            .mapVersion(draftMapVersion)
                            .floor(floor)
                            .kindCode(normalizeNodeKindCode(node.kind()))
                            .geomPx(point)
                            .nameKo(node.name())
                            .properties(node.properties() != null ? node.properties() : Map.of())
                            .source("manual")
                            .build()
            );
            requestIds.add(node.id());
        }

        List<Node> savedNodes = nodeRepository.saveAll(nodesToSave);
        Map<UUID, Node> savedNodesByRequestId = new HashMap<>();
        for (int i = 0; i < savedNodes.size(); i++) {
            UUID requestId = requestIds.get(i);
            if (requestId != null) {
                savedNodesByRequestId.put(requestId, savedNodes.get(i));
            }
        }
        return savedNodesByRequestId;
    }

    private void syncEntranceMappingsAfterFloorDraftSave(
            Map<UUID, Node> savedNodesByRequestId,
            List<BuildingEntranceMapping> cachedFloorMappings
    ) {
        if (cachedFloorMappings == null || cachedFloorMappings.isEmpty()) {
            return;
        }

        Map<UUID, UUID> newPoiIdByAnchorNodeId = poiRepository.findByAnchorNodeIdIn(
                        savedNodesByRequestId.values().stream().map(Node::getId).toList()
                ).stream()
                .collect(java.util.stream.Collectors.toMap(
                        Poi::getAnchorNodeId,
                        Poi::getId,
                        (existing, replacement) -> existing
                ));

        List<BuildingEntranceMapping> newMappings = new ArrayList<>();
        for (BuildingEntranceMapping cachedMapping : cachedFloorMappings) {
            UUID previousNodeId = cachedMapping.getEntranceNodeId();
            Node replacementNode = savedNodesByRequestId.get(previousNodeId);
            if (replacementNode == null) {
                continue;
            }

            UUID newPoiId = newPoiIdByAnchorNodeId.get(replacementNode.getId());
            newMappings.add(
                    BuildingEntranceMapping.builder()
                            .tenantId(cachedMapping.getTenantId())
                            .campusId(cachedMapping.getCampusId())
                            .buildingId(cachedMapping.getBuildingId())
                            .campusGateId(cachedMapping.getCampusGateId())
                            .entranceNodeId(replacementNode.getId())
                            .entrancePoiId(newPoiId)
                            .build()
            );
        }

        if (!newMappings.isEmpty()) {
            buildingEntranceMappingRepository.saveAll(newMappings);
        }
    }

    private void syncVerticalConnectorNodesAfterFloorDraftSave(
            Map<UUID, Node> savedNodesByRequestId,
            List<VerticalConnectorNode> cachedFloorConnectorNodes
    ) {
        if (cachedFloorConnectorNodes == null || cachedFloorConnectorNodes.isEmpty()) {
            return;
        }

        List<VerticalConnectorNode> newConnectorNodes = new ArrayList<>();
        for (VerticalConnectorNode cached : cachedFloorConnectorNodes) {
            UUID previousNodeId = cached.getNode().getId();
            Node replacementNode = savedNodesByRequestId.get(previousNodeId);
            if (replacementNode == null) {
                continue;
            }

            newConnectorNodes.add(
                    VerticalConnectorNode.builder()
                            .tenantId(cached.getTenantId())
                            .connector(cached.getConnector())
                            .node(replacementNode)
                            .floor(cached.getFloor())
                            .build()
            );
        }

        if (!newConnectorNodes.isEmpty()) {
            verticalConnectorNodeRepository.saveAll(newConnectorNodes);
        }
    }

    private void saveDraftZones(
            MapVersion draftMapVersion,
            Floor floor,
            List<MapEditorDraftZoneSaveDTO> zones
    ) {
        if (zones == null || zones.isEmpty()) {
            return;
        }

        List<Zone> zonesToSave = new ArrayList<>();
        for (MapEditorDraftZoneSaveDTO zone : zones) {
            Polygon polygon = toPolygon(zone.geomPx());
            if (polygon == null) {
                continue;
            }

            ZoneKind zoneKind = parseZoneKind(zone.kind());
            zonesToSave.add(
                    Zone.builder()
                            .tenantId(draftMapVersion.getTenantId())
                            .mapVersion(draftMapVersion)
                            .floor(floor)
                            .kind(zoneKind)
                            .name(zone.name())
                            .geomPx(polygon)
                            .properties(zone.properties() != null ? zone.properties() : Map.of())
                            .build()
            );
        }

        if (!zonesToSave.isEmpty()) {
            zoneRepository.saveAll(suppressDuplicateRoomZones(zonesToSave));
        }
    }

    private void saveDraftPois(
            MapVersion draftMapVersion,
            Floor floor,
            List<MapEditorDraftPoiSaveDTO> pois,
            Map<UUID, Node> savedNodesByRequestId,
            Map<UUID, Poi> existingPoisById
    ) {
        if (pois == null || pois.isEmpty()) {
            return;
        }

        Map<String, Long> categoryIdsByCode = loadPoiCategoryIdsFromCodes(
                pois.stream()
                        .map(poi -> normalizePoiCategoryCodeForDraft(poi.code(), poi.attrs()))
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList()
        );

        List<Poi> poisToSave = new ArrayList<>();
        for (MapEditorDraftPoiSaveDTO poi : pois) {
            Point point = toPoint(poi.geomPx());
            if (point == null) {
                continue;
            }

            String categoryCode = normalizePoiCategoryCodeForDraft(poi.code(), poi.attrs());
            UUID anchorNodeId = resolveSavedNodeId(poi.anchorNodeId(), savedNodesByRequestId);
            Poi existingPoi = poi.id() != null ? existingPoisById.get(poi.id()) : null;
            poisToSave.add(
                    Poi.builder()
                            .tenantId(draftMapVersion.getTenantId())
                            .mapVersion(draftMapVersion)
                            .floor(floor)
                            .categoryId(categoryCode != null ? categoryIdsByCode.get(categoryCode) : null)
                            .name(firstNonBlank(poi.name(), "새 POI"))
                            .code(categoryCode)
                            .geomPx(point)
                            .footprintPx(toPolygon(poi.footprintPx()))
                            .geomWgs84(existingPoi != null ? existingPoi.getGeomWgs84() : null)
                            .anchorNodeId(anchorNodeId)
                            .attrs(mergePoiAttrs(existingPoi != null ? existingPoi.getAttrs() : null, poi.attrs(), categoryCode))
                            .externalApiId(firstNonBlank(blankToNull(poi.externalApiId()), existingPoi != null ? existingPoi.getExternalApiId() : null))
                            .source(existingPoi != null ? existingPoi.getSource() : "manual")
                            .aiDetectionId(existingPoi != null ? existingPoi.getAiDetectionId() : null)
                            .build()
            );
        }

        if (!poisToSave.isEmpty()) {
            poiRepository.saveAll(poisToSave);
        }
    }

    private void saveDraftEdges(
            MapVersion draftMapVersion,
            List<MapEditorDraftEdgeSaveDTO> edges,
            Map<UUID, Node> savedNodesByRequestId
    ) {
        if (edges == null || edges.isEmpty()) {
            return;
        }

        List<Edge> edgesToSave = new ArrayList<>();
        for (MapEditorDraftEdgeSaveDTO edge : edges) {
            Node fromNode = resolveSavedNode(edge.fromNodeId(), savedNodesByRequestId);
            Node toNode = resolveSavedNode(edge.toNodeId(), savedNodesByRequestId);
            if (fromNode == null || toNode == null) {
                continue;
            }

            LineString lineString = toLineString(edge.geomPx());
            if (lineString == null) {
                lineString = GEOMETRY_FACTORY.createLineString(new Coordinate[]{
                        fromNode.getGeomPx().getCoordinate(),
                        toNode.getGeomPx().getCoordinate(),
                });
            }

            edgesToSave.add(
                    Edge.builder()
                            .tenantId(draftMapVersion.getTenantId())
                            .mapVersion(draftMapVersion)
                            .fromNode(fromNode)
                            .toNode(toNode)
                            .kindCode(firstNonBlank(edge.kind(), "walkway"))
                            .geomPx(lineString)
                            .isDirected(Boolean.TRUE.equals(edge.isDirected()))
                            .baseWeight(edge.baseWeight() != null ? edge.baseWeight() : BigDecimal.ONE)
                            .properties(edge.properties() != null ? edge.properties() : Map.of())
                            .source("manual")
                            .build()
            );
        }

        if (!edgesToSave.isEmpty()) {
            edgeRepository.saveAll(edgesToSave);
        }
    }

    private void saveEdges(
            MapVersion draftMapVersion,
            Floor floor,
            List<AiDetection> edgeDetections,
            Map<String, Node> nodeCache
    ) {
        if (edgeDetections.isEmpty()) {
            return;
        }

        List<Node> implicitNodesToSave = new ArrayList<>();
        for (AiDetection detection : edgeDetections) {
            LineString lineString = toLineString(detection.getGeomPx());
            if (lineString == null || lineString.getNumPoints() < 2) {
                continue;
            }

            ensureNodeForCoordinate(draftMapVersion, floor, lineString.getCoordinateN(0), nodeCache, implicitNodesToSave);
            ensureNodeForCoordinate(draftMapVersion, floor, lineString.getCoordinateN(lineString.getNumPoints() - 1), nodeCache, implicitNodesToSave);
        }

        if (!implicitNodesToSave.isEmpty()) {
            List<Node> savedNodes = nodeRepository.saveAll(implicitNodesToSave);
            for (Node savedNode : savedNodes) {
                nodeCache.put(toNodeCacheKey(savedNode.getGeomPx()), savedNode);
            }
        }

        List<Edge> edgesToSave = new ArrayList<>();
        for (AiDetection detection : edgeDetections) {
            LineString lineString = toLineString(detection.getGeomPx());
            if (lineString == null || lineString.getNumPoints() < 2) {
                continue;
            }

            Node fromNode = nodeCache.get(toNodeCacheKey(lineString.getCoordinateN(0)));
            Node toNode = nodeCache.get(toNodeCacheKey(lineString.getCoordinateN(lineString.getNumPoints() - 1)));
            if (fromNode == null || toNode == null) {
                continue;
            }

            edgesToSave.add(
                    Edge.builder()
                            .tenantId(draftMapVersion.getTenantId())
                            .mapVersion(draftMapVersion)
                            .fromNode(fromNode)
                            .toNode(toNode)
                            .kindCode("walkway")
                            .geomPx(lineString)
                            .properties(buildDetectionProperties(detection))
                            .source("ai")
                            .aiDetectionId(detection.getId())
                            .build()
            );
        }

        if (!edgesToSave.isEmpty()) {
            List<Edge> savedEdges = edgeRepository.saveAll(edgesToSave);
            savedEdges.forEach(edge -> edgeDetections.stream()
                    .filter(detection -> Objects.equals(detection.getId(), edge.getAiDetectionId()))
                    .findFirst()
                    .ifPresent(detection -> detection.markCommitted("edge", edge.getId())));
        }
    }

    private void ensureNodeForCoordinate(
            MapVersion draftMapVersion,
            Floor floor,
            Coordinate coordinate,
            Map<String, Node> nodeCache,
            List<Node> implicitNodesToSave
    ) {
        Point point = GEOMETRY_FACTORY.createPoint(coordinate);
        String key = toNodeCacheKey(point);
        if (nodeCache.containsKey(key)) {
            return;
        }

        Node node = Node.builder()
                .tenantId(draftMapVersion.getTenantId())
                .mapVersion(draftMapVersion)
                .floor(floor)
                .kindCode("corridor")
                .geomPx(point)
                .properties(Map.of("generatedFrom", "edge_candidate"))
                .source("ai")
                .build();
        nodeCache.put(key, node);
        implicitNodesToSave.add(node);
    }

    private Optional<Zone> buildZone(MapVersion mapVersion, Floor floor, AiDetection detection, ZoneKind zoneKind) {
        if (!(detection.getGeomPx() instanceof Polygon polygon)) {
            return Optional.empty();
        }
        return Optional.of(
                Zone.builder()
                        .tenantId(mapVersion.getTenantId())
                        .mapVersion(mapVersion)
                        .floor(floor)
                        .kind(zoneKind)
                        .name(null)
                        .geomPx(polygon)
                        .properties(buildDetectionProperties(detection))
                        .build()
        );
    }

    private Zone enrichZoneNameFromText(Zone zone, List<AiDetection> textDetections) {
        Polygon polygon = zone.getGeomPx();
        String bestText = findBestMatchingTextForZone(polygon, textDetections);
        if (bestText == null) {
            return zone;
        }

        return Zone.builder()
                .tenantId(zone.getTenantId())
                .mapVersion(zone.getMapVersion())
                .floor(zone.getFloor())
                .kind(zone.getKind())
                .name(bestText)
                .geomPx(zone.getGeomPx())
                .properties(zone.getProperties())
                .build();
    }

    private Optional<FloorplanObject> buildFloorplanObject(
            MapVersion mapVersion,
            Floor floor,
            AiDetection detection,
            String kind
    ) {
        Geometry geometry = detection.getGeomPx();
        if (geometry == null) {
            return Optional.empty();
        }
        return Optional.of(
                FloorplanObject.builder()
                        .tenantId(mapVersion.getTenantId())
                        .mapVersion(mapVersion)
                        .floor(floor)
                        .kind(kind)
                        .geomPx(geometry)
                        .properties(buildDetectionProperties(detection))
                        .source("ai")
                        .aiDetectionId(detection.getId())
                        .build()
        );
    }

    private Optional<Poi> buildPoi(
            MapVersion mapVersion,
            Floor floor,
            AiDetection detection,
            Map<String, Long> poiCategoryIdsByCode
    ) {
        if (!isPoiDetection(detection.getDetectType())) {
            return Optional.empty();
        }

        Point point = toPoint(detection.getGeomPx());
        if (point == null) {
            return Optional.empty();
        }

        Geometry geometry = detection.getGeomPx();
        Polygon footprint = geometry instanceof Polygon polygon ? polygon : null;
        String categoryCode = resolvePoiCategoryCode(detection.getDetectType());
        Long categoryId = categoryCode != null ? poiCategoryIdsByCode.get(categoryCode) : null;
        Map<String, Object> properties = new LinkedHashMap<>(buildDetectionProperties(detection));
        if (categoryCode != null) {
            properties.put("label", categoryCode);
        }

        return Optional.of(
                Poi.builder()
                        .tenantId(mapVersion.getTenantId())
                        .mapVersion(mapVersion)
                        .floor(floor)
                        .categoryId(categoryId)
                        .name(resolvePoiName(detection))
                        .code(categoryCode)
                        .geomPx(point)
                        .footprintPx(footprint)
                        .attrs(properties)
                        .source("ai")
                        .aiDetectionId(detection.getId())
                        .build()
        );
    }

    private Map<String, Long> loadPoiCategoryIds(List<AiDetection> detections) {
        Set<String> categoryCodes = detections.stream()
                .map(AiDetection::getDetectType)
                .map(this::resolvePoiCategoryCode)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        if (categoryCodes.isEmpty()) {
            return Map.of();
        }

        return poiCategoryRepository.findByCodeIn(categoryCodes).stream()
                .collect(java.util.stream.Collectors.toMap(
                        PoiCategory::getCode,
                        PoiCategory::getId,
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ));
    }

    private Poi enrichPoiNameFromText(Poi poi, AiDetection poiDetection, List<AiDetection> textDetections) {
        if (isFacilityPoiDetection(poiDetection)) {
            return poi;
        }

        String ownReadableText = extractReadableText(poiDetection);
        if (ownReadableText != null) {
            return poi;
        }

        String matchedText = findBestMatchingTextForPoi(poiDetection, textDetections);
        if (matchedText == null) {
            return poi;
        }

        return Poi.builder()
                .tenantId(poi.getTenantId())
                .mapVersion(poi.getMapVersion())
                .floor(poi.getFloor())
                .categoryId(poi.getCategoryId())
                .name(matchedText)
                .code(poi.getCode())
                .geomPx(poi.getGeomPx())
                .footprintPx(poi.getFootprintPx())
                .geomWgs84(poi.getGeomWgs84())
                .anchorNodeId(poi.getAnchorNodeId())
                .tags(poi.getTags())
                .attrs(poi.getAttrs())
                .externalApiId(poi.getExternalApiId())
                .source(poi.getSource())
                .aiDetectionId(poi.getAiDetectionId())
                .build();
    }

    private List<Poi> suppressGenericPoiCandidatesNearFacilities(List<Poi> pois) {
        List<Poi> facilityPois = pois.stream()
                .filter(this::isFacilityPoi)
                .toList();

        if (facilityPois.isEmpty()) {
            return pois;
        }

        List<Poi> filtered = new ArrayList<>();
        for (Poi poi : pois) {
            if (!isGenericPoiCandidate(poi)) {
                filtered.add(poi);
                continue;
            }

            boolean overlapsFacility = facilityPois.stream()
                    .anyMatch(facilityPoi -> isSameOrNearbyPoi(facilityPoi, poi));

            if (!overlapsFacility) {
                filtered.add(poi);
            }
        }
        return filtered;
    }

    private List<Zone> suppressDuplicateRoomZones(List<Zone> zones) {
        List<Zone> filtered = new ArrayList<>();

        for (Zone zone : zones) {
            if (zone.getKind() != ZoneKind.room) {
                filtered.add(zone);
                continue;
            }

            int duplicateIndex = -1;
            for (int i = 0; i < filtered.size(); i++) {
                Zone existing = filtered.get(i);
                if (isDuplicateRoomZone(existing, zone)) {
                    duplicateIndex = i;
                    if (shouldPreferRoomZone(zone, existing)) {
                        filtered.set(i, zone);
                    }
                    break;
                }
            }

            if (duplicateIndex < 0) {
                filtered.add(zone);
            }
        }

        return filtered;
    }

    private Map<String, Object> buildDetectionProperties(AiDetection detection) {
        Map<String, Object> properties = new LinkedHashMap<>();
        if(detection.getAttrs() != null && !detection.getAttrs().isEmpty()) {
            properties.putAll(detection.getAttrs());
        }
        properties.put("detectType",detection.getDetectType());
        if (detection.getConfidence() != null) {
            properties.put("confidence", detection.getConfidence().setScale(4, RoundingMode.HALF_UP));
        }
        if (detection.getOcrText() != null && !detection.getOcrText().isBlank()) {
            properties.put("ocrText", detection.getOcrText());
        }
        if (detection.getLabel() != null && !detection.getLabel().isBlank()) {
            properties.put("label", detection.getLabel());
        }
        properties.put("aiDetectionId", detection.getId());
        if (detection.getAttrs() != null && !detection.getAttrs().isEmpty()) {
            properties.putAll(detection.getAttrs());
        }
        return properties;
    }

    private String normalizeDetectType(String detectType) {
        return detectType == null ? "" : detectType.trim().toLowerCase();
    }

    private boolean isPoiDetection(String detectType) {
        return resolvePoiCategoryCode(detectType) != null || "poi_candidate".equals(normalizeDetectType(detectType));
    }

    private boolean isFacilityPoiDetection(AiDetection detection) {
        String categoryCode = resolvePoiCategoryCode(detection.getDetectType());
        return categoryCode != null && categoryCode.startsWith("facility.");
    }

    private String resolvePoiCategoryCode(String detectType) {
        return switch (normalizeDetectType(detectType)) {
            case "accessible_restroom" -> "facility.accessible_restroom";
            case "aed" -> "facility.aed";
            case "atm" -> "facility.atm";
            case "cafe" -> "store.cafe";
            case "clothing_alteration" -> "store.clothing_alteration";
            case "elevator" -> "facility.elevator";
            case "escalator" -> "facility.escalator";
            case "facility.elevator" -> "facility.elevator";
            case "facility.escalator" -> "facility.escalator";
            case "facility.infodesk" -> "facility.infodesk";
            case "facility.stair" -> "facility.stair";
            case "family_restroom" -> "facility.family_restroom";
            case "infodesk" -> "facility.infodesk";
            case "phone_charging" -> "facility.phone_charging";
            case "restroom_female" -> "facility.restroom_female";
            case "restroom_male" -> "facility.restroom_male";
            case "restroom_sign" -> "facility.restroom";
            case "shoe_repair" -> "store.shoe_repair";
            case "stair" -> "facility.stair";
            case "storage_locker" -> "facility.storage_locker";
            case "subway_station" -> "facility.subway_station";
            case "water_fountain" -> "facility.water_fountain";
            default -> null;
        };
    }

    private String normalizePoiCategoryCodeForDraft(String code, Map<String, Object> attrs) {
        String normalizedCode = blankToNull(code);
        if (normalizedCode != null) {
            return normalizedCode;
        }

        Object label = attrs != null ? attrs.get("label") : null;
        if (label instanceof String labelValue && !labelValue.isBlank()) {
            return labelValue;
        }
        return null;
    }

    private Map<String, Long> loadPoiCategoryIdsFromCodes(Collection<String> categoryCodes) {
        Set<String> normalizedCodes = categoryCodes.stream()
                .filter(Objects::nonNull)
                .filter(code -> !code.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        if (normalizedCodes.isEmpty()) {
            return Map.of();
        }

        return poiCategoryRepository.findByCodeIn(normalizedCodes).stream()
                .collect(java.util.stream.Collectors.toMap(
                        PoiCategory::getCode,
                        PoiCategory::getId,
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ));
    }

    private Map<String, Object> mergePoiAttrs(Map<String, Object> existingAttrs, Map<String, Object> attrs, String categoryCode) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (existingAttrs != null && !existingAttrs.isEmpty()) {
            merged.putAll(existingAttrs);
        }
        if (attrs != null && !attrs.isEmpty()) {
            merged.putAll(attrs);
        }
        if (categoryCode != null) {
            merged.put("label", categoryCode);
        }
        return merged;
    }

    private String resolvePoiName(AiDetection detection) {
        String preferred = extractReadableText(detection);
        if (preferred != null) {
            return preferred;
        }

        return switch (normalizeDetectType(detection.getDetectType())) {
            case "accessible_restroom" -> "장애인 화장실";
            case "aed" -> "AED";
            case "atm" -> "ATM";
            case "cafe" -> "카페";
            case "clothing_alteration" -> "수선실";
            case "elevator" -> "엘리베이터";
            case "escalator" -> "에스컬레이터";
            case "facility.elevator" -> "엘리베이터";
            case "facility.escalator" -> "에스컬레이터";
            case "facility.infodesk" -> "안내데스크";
            case "facility.stair" -> "계단";
            case "family_restroom" -> "가족 화장실";
            case "infodesk" -> "안내데스크";
            case "phone_charging" -> "휴대폰 충전";
            case "restroom_female" -> "여자 화장실";
            case "restroom_male" -> "남자 화장실";
            case "restroom_sign" -> "화장실";
            case "shoe_repair" -> "구두수선";
            case "stair" -> "계단";
            case "storage_locker" -> "물품보관함";
            case "subway_station" -> "지하철역";
            case "water_fountain" -> "정수기";
            case "poi_candidate" -> "POI 후보";
            default -> "POI";
        };
    }

    private boolean isVerticalFacilityDetectType(String detectType) {
        return switch (normalizeDetectType(detectType)) {
            case "stair", "elevator", "escalator" -> true;
            default -> false;
        };
    }

    private String findBestMatchingTextForPoi(AiDetection poiDetection, List<AiDetection> textDetections) {
        if (textDetections.isEmpty()) {
            return null;
        }

        Geometry poiGeometry = poiDetection.getGeomPx();
        Point poiPoint = toPoint(poiGeometry);
        if (poiGeometry == null || poiPoint == null) {
            return null;
        }

        String bestText = null;
        double bestScore = Double.MAX_VALUE;

        for (AiDetection textDetection : textDetections) {
            String candidateText = extractReadableText(textDetection);
            if (candidateText == null) {
                continue;
            }

            Point textPoint = toPoint(textDetection.getGeomPx());
            if (textPoint == null) {
                continue;
            }

            double distance = poiGeometry.distance(textPoint);
            boolean inside = poiGeometry instanceof Polygon polygon && polygon.buffer(4).contains(textPoint);
            double score = inside ? distance : distance + 180.0;

            if (!inside && distance > 140.0) {
                continue;
            }

            if (score < bestScore) {
                bestScore = score;
                bestText = candidateText;
            }
        }

        return bestText;
    }

    private String findBestMatchingTextForZone(Polygon zonePolygon, List<AiDetection> textDetections) {
        if (textDetections.isEmpty()) {
            return null;
        }

        Point centroid = zonePolygon.getCentroid();
        String bestText = null;
        double bestScore = Double.MAX_VALUE;

        for (AiDetection textDetection : textDetections) {
            String candidateText = extractReadableText(textDetection);
            if (candidateText == null) {
                continue;
            }

            Point textPoint = toPoint(textDetection.getGeomPx());
            if (textPoint == null) {
                continue;
            }

            boolean inside = zonePolygon.buffer(4).contains(textPoint);
            if (!inside) {
                continue;
            }

            double distanceScore = centroid != null ? centroid.distance(textPoint) : 0.0;
            double score = distanceScore;

            if (score < bestScore) {
                bestScore = score;
                bestText = candidateText;
            }
        }

        return bestText;
    }

    private String extractReadableText(AiDetection detection) {
        String ocrText = sanitizeNameCandidate(detection.getOcrText());
        if (ocrText != null) {
            return ocrText;
        }

        return sanitizeNameCandidate(detection.getLabel());
    }

    private String sanitizeNameCandidate(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            return null;
        }

        String normalized = trimmed.toLowerCase();
        if (normalized.startsWith("facility.")
                || normalized.startsWith("store.")
                || normalized.contains("_")
                || normalized.equals("poi_candidate")
                || normalized.equals("poi")) {
            return null;
        }

        return trimmed;
    }

    private boolean isDuplicateRoomZone(Zone existing, Zone candidate) {
        if (existing.getKind() != ZoneKind.room || candidate.getKind() != ZoneKind.room) {
            return false;
        }

        String existingName = normalizeZoneName(existing.getName());
        String candidateName = normalizeZoneName(candidate.getName());
        if (existingName != null && candidateName != null && !existingName.equals(candidateName)) {
            return false;
        }

        Polygon existingPolygon = existing.getGeomPx();
        Polygon candidatePolygon = candidate.getGeomPx();
        if (existingPolygon == null || candidatePolygon == null) {
            return false;
        }

        if (!existingPolygon.intersects(candidatePolygon)) {
            return false;
        }

        double existingArea = existingPolygon.getArea();
        double candidateArea = candidatePolygon.getArea();
        double minArea = Math.min(existingArea, candidateArea);
        if (minArea <= 0.0) {
            return false;
        }

        double overlapArea = existingPolygon.intersection(candidatePolygon).getArea();
        return (overlapArea / minArea) >= 0.92;
    }

    private boolean shouldPreferRoomZone(Zone candidate, Zone existing) {
        boolean candidateNamed = normalizeZoneName(candidate.getName()) != null;
        boolean existingNamed = normalizeZoneName(existing.getName()) != null;

        if (candidateNamed != existingNamed) {
            return candidateNamed;
        }

        return candidate.getGeomPx().getArea() > existing.getGeomPx().getArea();
    }

    private String normalizeZoneName(String name) {
        if (name == null) {
            return null;
        }

        String trimmed = name.trim();
        return trimmed.isBlank() ? null : trimmed.toLowerCase();
    }

    private String resolveDetectionName(AiDetection detection) {
        return extractReadableText(detection);
    }

    private boolean isFacilityPoi(Poi poi) {
        String detectType = getPoiDetectType(poi);
        return resolvePoiCategoryCode(detectType) != null && resolvePoiCategoryCode(detectType).startsWith("facility.");
    }

    private boolean isGenericPoiCandidate(Poi poi) {
        return "poi_candidate".equals(normalizeDetectType(getPoiDetectType(poi)));
    }

    private String getPoiDetectType(Poi poi) {
        Object value = poi.getAttrs() != null ? poi.getAttrs().get("detectType") : null;
        return value instanceof String stringValue ? stringValue : null;
    }

    private boolean isSameOrNearbyPoi(Poi facilityPoi, Poi genericPoi) {
        Point facilityPoint = toPoint(facilityPoi.getGeomPx());
        Point genericPoint = toPoint(genericPoi.getGeomPx());
        if (facilityPoint == null || genericPoint == null) {
            return false;
        }

        if (facilityPoint.distance(genericPoint) <= 36.0) {
            return true;
        }

        Polygon facilityFootprint = facilityPoi.getFootprintPx();
        Polygon genericFootprint = genericPoi.getFootprintPx();
        if (facilityFootprint != null && genericFootprint != null) {
            return facilityFootprint.intersects(genericFootprint)
                    || facilityFootprint.buffer(4).intersects(genericFootprint)
                    || genericFootprint.buffer(4).intersects(facilityFootprint);
        }

        if (facilityFootprint != null) {
            return facilityFootprint.buffer(4).contains(genericPoint);
        }

        if (genericFootprint != null) {
            return genericFootprint.buffer(4).contains(facilityPoint);
        }

        return false;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ZoneKind parseZoneKind(String value) {
        if (value == null || value.isBlank()) {
            return ZoneKind.room;
        }
        try {
            return ZoneKind.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return ZoneKind.room;
        }
    }

    private String resolveNodeKindCode(String detectType) {
        return switch (normalizeDetectType(detectType)) {
            case "entrance" -> "entrance";
            case "stair" -> "stair";
            case "elevator" -> "elevator";
            case "escalator" -> "escalator";
            default -> "corridor";
        };
    }

    private String normalizeNodeKindCode(String value) {
        if (value == null || value.isBlank()) {
            return "corridor";
        }
        return switch (value.trim().toLowerCase()) {
            case "entrance" -> "entrance";
            case "stair" -> "stair";
            case "elevator" -> "elevator";
            case "escalator" -> "escalator";
            case "door" -> "door";
            case "poi_anchor" -> "poi_anchor";
            case "portal" -> "portal";
            case "corridor", "node" -> "corridor";
            default -> "corridor";
        };
    }

    private record FloorDraftContentState(
            boolean hasZones,
            boolean hasFloorplanObjects,
            boolean hasEdges,
            boolean hasNodes,
            boolean hasPois
    ) {
        private boolean hasAnyContent() {
            return hasZones || hasFloorplanObjects || hasEdges || hasNodes || hasPois;
        }

        private boolean hasMissingContent() {
            return !hasZones || !hasFloorplanObjects || !hasEdges || !hasNodes || !hasPois;
        }

        private boolean isFullyReady() {
            return hasZones && hasFloorplanObjects && hasEdges && hasNodes && hasPois;
        }
    }

    private Point toPoint(Geometry geometry) {
        if (geometry == null) {
            return null;
        }
        if (geometry instanceof Point point) {
            return point;
        }
        Point centroid = geometry.getCentroid();
        return centroid != null ? GEOMETRY_FACTORY.createPoint(centroid.getCoordinate()) : null;
    }

    private Point toPoint(Map<String, Object> geometry) {
        Geometry parsedGeometry = toGeometry(geometry);
        return toPoint(parsedGeometry);
    }

    private LineString toLineString(Geometry geometry) {
        if (geometry instanceof LineString lineString) {
            return lineString;
        }
        return null;
    }

    private LineString toLineString(Map<String, Object> geometry) {
        Geometry parsedGeometry = toGeometry(geometry);
        return parsedGeometry instanceof LineString lineString ? lineString : null;
    }

    private Polygon toPolygon(Map<String, Object> geometry) {
        Geometry parsedGeometry = toGeometry(geometry);
        return parsedGeometry instanceof Polygon polygon ? polygon : null;
    }

    private Geometry toGeometry(Map<String, Object> geometry) {
        if (geometry == null || geometry.isEmpty()) {
            return null;
        }

        Object typeValue = geometry.get("type");
        Object coordinatesValue = geometry.get("coordinates");

        if (!(typeValue instanceof String type) || coordinatesValue == null) {
            return null;
        }

        return switch (type) {
            case "Point" -> GEOMETRY_FACTORY.createPoint(toCoordinate(coordinatesValue));
            case "LineString" -> GEOMETRY_FACTORY.createLineString(asCoordinateArray(coordinatesValue, 2));
            case "Polygon" -> {
                List<?> rings = asList(coordinatesValue);
                if (rings.isEmpty()) {
                    yield null;
                }
                Coordinate[] shellCoordinates = requireValidRing(asCoordinateArray(rings.get(0), 4));
                LinearRing shell = GEOMETRY_FACTORY.createLinearRing(closeRing(shellCoordinates));
                LinearRing[] holes = rings.stream()
                        .skip(1)
                        .map(this::asCoordinateArrayFromRing)
                        .map(this::requireValidRing)
                        .map(this::closeRing)
                        .map(GEOMETRY_FACTORY::createLinearRing)
                        .toArray(LinearRing[]::new);
                yield GEOMETRY_FACTORY.createPolygon(shell, holes);
            }
            default -> null;
        };
    }

    private Coordinate[] asCoordinateArrayFromRing(Object value) {
        return asCoordinateArray(value, 4);
    }

    private Coordinate[] asCoordinateArray(Object value, int minSize) {
        List<?> coordinateList = asList(value);
        if (coordinateList.size() < minSize) {
            throw new AiException(AiErrorCode.AI_INVALID_DETECTION_GEOMETRY);
        }
        return coordinateList.stream()
                .map(this::toCoordinate)
                .toArray(Coordinate[]::new);
    }

    private Coordinate[] closeRing(Coordinate[] coordinates) {
        Coordinate first = coordinates[0];
        Coordinate last = coordinates[coordinates.length - 1];
        if (first.equals2D(last)) {
            return coordinates;
        }
        Coordinate[] closed = new Coordinate[coordinates.length + 1];
        System.arraycopy(coordinates, 0, closed, 0, coordinates.length);
        closed[closed.length - 1] = new Coordinate(first.x, first.y);
        return closed;
    }

    private Coordinate[] requireValidRing(Coordinate[] coordinates) {
        if (coordinates.length < 4) {
            throw new AiException(AiErrorCode.AI_INVALID_DETECTION_GEOMETRY);
        }
        return coordinates;
    }

    private Coordinate toCoordinate(Object value) {
        List<?> pair = asList(value);
        if (pair.size() < 2 || !(pair.get(0) instanceof Number x) || !(pair.get(1) instanceof Number y)) {
            throw new AiException(AiErrorCode.AI_INVALID_DETECTION_GEOMETRY);
        }
        return new Coordinate(x.doubleValue(), y.doubleValue());
    }

    private List<?> asList(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        throw new AiException(AiErrorCode.AI_INVALID_DETECTION_GEOMETRY);
    }

    private Node resolveSavedNode(UUID requestNodeId, Map<UUID, Node> savedNodesByRequestId) {
        return requestNodeId != null ? savedNodesByRequestId.get(requestNodeId) : null;
    }

    private UUID resolveSavedNodeId(UUID requestNodeId, Map<UUID, Node> savedNodesByRequestId) {
        Node savedNode = resolveSavedNode(requestNodeId, savedNodesByRequestId);
        return savedNode != null ? savedNode.getId() : null;
    }

    private String toNodeCacheKey(Point point) {
        Coordinate coordinate = point.getCoordinate();
        return "%.3f:%.3f".formatted(coordinate.x, coordinate.y);
    }

    private String toNodeCacheKey(Coordinate coordinate) {
        return "%.3f:%.3f".formatted(coordinate.x, coordinate.y);
    }

    private UUID extractAiDetectionId(Zone zone) {
        Object value = zone.getProperties().get("aiDetectionId");
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return UUID.fromString(stringValue);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    @Transactional(readOnly = true)
    public List<MapEditorVerticalConnectorDTO> getVerticalConnectors(UUID tenantId, UUID buildingId, UUID userId) {
        Building building = buildingRepository.findByIdAndTenant_Id(buildingId, tenantId)
                .orElseThrow(() -> new BuildingException(BuildingErrorCode.BUILDING_NOT_FOUND));

        Optional<MapVersion> draftMapVersionOpt = mapVersionRepository
                .findFirstByBuildingIdAndMapTypeAndStatusOrderByCreatedAtDesc(building.getId(), MapType.BUILDING, "draft");
        if (draftMapVersionOpt.isEmpty()) {
            return List.of();
        }
        MapVersion draftMapVersion = draftMapVersionOpt.get();

        List<VerticalConnector> connectors = verticalConnectorRepository.findByMapVersionId(draftMapVersion.getId());
        List<VerticalConnectorNode> connectorNodes = verticalConnectorNodeRepository.findByConnectorMapVersionId(draftMapVersion.getId());

        Map<UUID, List<MapEditorVerticalConnectorNodeDTO>> nodesByConnectorId = new HashMap<>();
        for (VerticalConnectorNode cn : connectorNodes) {
            UUID connectorId = cn.getConnector().getId();
            MapEditorVerticalConnectorNodeDTO nodeDTO = new MapEditorVerticalConnectorNodeDTO(
                    cn.getFloor().getId(),
                    cn.getFloor().getName(),
                    cn.getNode().getId(),
                    cn.getNode().getNameKo() != null ? cn.getNode().getNameKo() : (cn.getNode().getKindCode() + " 노드")
            );
            nodesByConnectorId.computeIfAbsent(connectorId, k -> new ArrayList<>()).add(nodeDTO);
        }

        List<MapEditorVerticalConnectorDTO> result = new ArrayList<>();
        for (VerticalConnector c : connectors) {
            result.add(new MapEditorVerticalConnectorDTO(
                    c.getId(),
                    c.getKind(),
                    c.getName(),
                    nodesByConnectorId.getOrDefault(c.getId(), List.of())
            ));
        }
        return result;
    }

    @Transactional
    public MapEditorVerticalConnectorDTO createVerticalConnector(
            UUID tenantId,
            UUID buildingId,
            UUID userId,
            MapEditorVerticalConnectorCreateRequestDTO request
    ) {
        Building building = buildingRepository.findByIdAndTenant_Id(buildingId, tenantId)
                .orElseThrow(() -> new BuildingException(BuildingErrorCode.BUILDING_NOT_FOUND));

        DraftMapVersionResult draftResult = getOrCreateBuildingDraftMapVersion(building, userId);
        MapVersion draftMapVersion = draftResult.mapVersion();

        VerticalConnector connector = VerticalConnector.builder()
                .tenantId(tenantId)
                .mapVersion(draftMapVersion)
                .building(building)
                .kind(request.kind())
                .name(request.name())
                .direction("both")
                .accessibility(Map.of())
                .build();

        VerticalConnector saved = verticalConnectorRepository.save(connector);
        return new MapEditorVerticalConnectorDTO(saved.getId(), saved.getKind(), saved.getName(), List.of());
    }

    @Transactional
    public void deleteVerticalConnector(UUID tenantId, UUID buildingId, UUID connectorId, UUID userId) {
        VerticalConnector connector = verticalConnectorRepository.findById(connectorId)
                .orElseThrow(() -> new BuildingException(BuildingErrorCode.BUILDING_NOT_FOUND));

        if (!connector.getTenantId().equals(tenantId) || !connector.getBuilding().getId().equals(buildingId)) {
            throw new BuildingException(BuildingErrorCode.BUILDING_NOT_FOUND);
        }

        MapVersion draftMapVersion = getDraftMapVersionOrThrow(buildingId);
        if (!connector.getMapVersion().getId().equals(draftMapVersion.getId())) {
            throw new MapException(MapErrorCode.MAP_VERSION_NOT_EDITABLE);
        }

        verticalConnectorNodeRepository.deleteByConnectorId(connectorId);
        verticalConnectorRepository.delete(connector);
    }

    @Transactional
    public MapEditorVerticalConnectorDTO mapVerticalConnectorNode(
            UUID tenantId,
            UUID buildingId,
            UUID connectorId,
            UUID userId,
            MapEditorVerticalConnectorMapRequestDTO request
    ) {
        VerticalConnector connector = verticalConnectorRepository.findById(connectorId)
                .orElseThrow(() -> new BuildingException(BuildingErrorCode.BUILDING_NOT_FOUND));

        if (!connector.getTenantId().equals(tenantId) || !connector.getBuilding().getId().equals(buildingId)) {
            throw new BuildingException(BuildingErrorCode.BUILDING_NOT_FOUND);
        }

        MapVersion draftMapVersion = getDraftMapVersionOrThrow(buildingId);
        if (!connector.getMapVersion().getId().equals(draftMapVersion.getId())) {
            throw new MapException(MapErrorCode.MAP_VERSION_NOT_EDITABLE);
        }

        Node node = nodeRepository.findById(request.nodeId())
                .orElseThrow(() -> new BuildingException(BuildingErrorCode.BUILDING_NOT_FOUND));

        Floor floor = floorRepository.findById(request.floorId())
                .orElseThrow(() -> new BuildingException(BuildingErrorCode.FLOOR_NOT_FOUND));

        if (!tenantId.equals(node.getTenantId())) {
            throw new MapException(MapErrorCode.VERTICAL_CONNECTOR_TENANT_MISMATCH);
        }

        if (node.getFloor() == null || !request.floorId().equals(node.getFloor().getId())) {
            throw new MapException(MapErrorCode.VERTICAL_CONNECTOR_FLOOR_MISMATCH);
        }

        if (node.getMapVersion() == null || node.getMapVersion().getBuilding() == null
                || !buildingId.equals(node.getMapVersion().getBuilding().getId())) {
            throw new MapException(MapErrorCode.BUILDING_MISMATCH);
        }

        if (!node.getMapVersion().getId().equals(draftMapVersion.getId())) {
            throw new MapException(MapErrorCode.MAP_VERSION_NOT_EDITABLE);
        }

        if (floor.getBuilding() == null || !buildingId.equals(floor.getBuilding().getId())) {
            throw new BuildingException(BuildingErrorCode.BUILDING_FLOOR_MISMATCH);
        }

        // 1. 해당 커넥터의 동일 층 기존 연결 삭제
        verticalConnectorNodeRepository.deleteByConnectorIdAndFloorId(connectorId, request.floorId());
        verticalConnectorNodeRepository.flush();

        // 2. 신규 매핑 생성
        VerticalConnectorNode mapping = VerticalConnectorNode.builder()
                .tenantId(tenantId)
                .connector(connector)
                .node(node)
                .floor(floor)
                .build();

        verticalConnectorNodeRepository.save(mapping);
        verticalConnectorNodeRepository.flush();

        // 3. 갱신된 커넥터 정보 리턴
        return getVerticalConnectors(tenantId, buildingId, userId).stream()
                .filter(c -> c.id().equals(connectorId))
                .findFirst()
                .orElse(null);
    }

    @Transactional
    public MapEditorVerticalConnectorDTO unmapVerticalConnectorNode(
            UUID tenantId,
            UUID buildingId,
            UUID connectorId,
            UUID floorId,
            UUID userId
    ) {
        VerticalConnector connector = verticalConnectorRepository.findById(connectorId)
                .orElseThrow(() -> new BuildingException(BuildingErrorCode.BUILDING_NOT_FOUND));

        if (!connector.getTenantId().equals(tenantId) || !connector.getBuilding().getId().equals(buildingId)) {
            throw new BuildingException(BuildingErrorCode.BUILDING_NOT_FOUND);
        }

        MapVersion draftMapVersion = getDraftMapVersionOrThrow(buildingId);
        if (!connector.getMapVersion().getId().equals(draftMapVersion.getId())) {
            throw new MapException(MapErrorCode.MAP_VERSION_NOT_EDITABLE);
        }

        verticalConnectorNodeRepository.deleteByConnectorIdAndFloorId(connectorId, floorId);
        verticalConnectorNodeRepository.flush();

        return getVerticalConnectors(tenantId, buildingId, userId).stream()
                .filter(c -> c.id().equals(connectorId))
                .findFirst()
                .orElse(null);
    }

    private MapVersion getDraftMapVersionOrThrow(UUID buildingId) {
        return mapVersionRepository
                .findFirstByBuildingIdAndMapTypeAndStatusOrderByCreatedAtDesc(buildingId, MapType.BUILDING, "draft")
                .orElseThrow(() -> new MapException(MapErrorCode.MAP_VERSION_NOT_FOUND));
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

        validatePublishPreconditions(tenantId, building, draftMapVersion);

        List<MapVersion> existingPublished = mapVersionRepository.findAllByBuildingIdAndMapTypeAndStatus(buildingId, MapType.BUILDING, "published");
        for (MapVersion pv : existingPublished) {
            pv.archive();
        }
        // Flush the archive updates first so the partial unique index on published versions
        // sees no active published row before we promote the draft version.
        mapVersionRepository.saveAllAndFlush(existingPublished);

        draftMapVersion.publish();
        mapVersionRepository.save(draftMapVersion);
        building.updateActivationStatus("active");
        
        long entranceCount = nodeRepository.countByMapVersion_Building_IdAndKindCodeAndMapVersion_Status(buildingId, "entrance", "published");
        building.updateEntranceCount((int) entranceCount);
        if (building.getTenant() != null && !"approved".equals(building.getTenant().getStatus())) {
            building.getTenant().updateStatus("approved");
        }

        Campus campus = building.getCampus();
        if (campus != null && campus.getMeta() != null) {
            Object gatesValue = campus.getMeta().get("gates");
            if (gatesValue instanceof List<?> gates) {
                Map<String, Coordinate> gateCoordsById = new HashMap<>();
                for (Object item : gates) {
                    if (item instanceof Map<?, ?> entry) {
                        Object idObj = entry.get("id");
                        Object locObj = entry.get("location");
                        if (idObj != null && locObj instanceof Map<?, ?> loc) {
                            Object lonObj = loc.get("longitude");
                            Object latObj = loc.get("latitude");
                            if (lonObj instanceof Number lonNum && latObj instanceof Number latNum) {
                                gateCoordsById.put(String.valueOf(idObj), new Coordinate(lonNum.doubleValue(), latNum.doubleValue()));
                            }
                        }
                    }
                }

                List<BuildingEntranceMapping> mappings = buildingEntranceMappingRepository.findAllByTenantIdAndBuildingIdOrderByCreatedAtAsc(tenantId, buildingId);
                List<MapPointPair> pairs = new ArrayList<>();
                for (BuildingEntranceMapping m : mappings) {
                    Node node = nodeRepository.findById(m.getEntranceNodeId()).orElse(null);
                    Coordinate gateCoord = gateCoordsById.get(m.getCampusGateId());
                    if (node != null && node.getGeomPx() != null && gateCoord != null) {
                        pairs.add(new MapPointPair(
                                node.getGeomPx().getX(),
                                node.getGeomPx().getY(),
                                gateCoord.x,
                                gateCoord.y
                        ));
                    }
                }

                List<Poi> draftPois = poiRepository.findByMapVersionId(draftMapVersion.getId());
                for (Poi p : draftPois) {
                    if (p.getGeomWgs84() != null && p.getGeomPx() != null && p.getExternalApiId() != null) {
                        pairs.add(new MapPointPair(
                                p.getGeomPx().getX(),
                                p.getGeomPx().getY(),
                                p.getGeomWgs84().getX(),
                                p.getGeomWgs84().getY()
                        ));
                    }
                }

                List<Double> affine = calculateAffineTransform(pairs);
                if (affine != null) {
                    double a = affine.get(0);
                    double b = affine.get(1);
                    double d = affine.get(2);
                    double e = affine.get(3);
                    double xoff = affine.get(4);
                    double yoff = affine.get(5);

                    for (Floor floor : floors) {
                        Floorplan floorplan = floorplanRepository.findByFloorIdAndIsCurrentTrue(floor.getId()).orElse(null);
                        if (floorplan != null) {
                            FloorplanCalibration calibration = floorplanCalibrationRepository.findTopByFloorplanIdOrderByCreatedAtDesc(floorplan.getId())
                                    .orElse(FloorplanCalibration.builder()
                                            .tenantId(tenantId)
                                            .floorplan(floorplan)
                                            .gcp(Map.of())
                                            .build());
                            calibration.updateAffine(affine);
                            floorplanCalibrationRepository.save(calibration);
                        }
                    }

                    UUID mapVersionId = draftMapVersion.getId();

                    entityManager.createNativeQuery(
                            "UPDATE node n " +
                            "SET geom_wgs84 = CAST(ST_Force3D(ST_SetSRID(ST_Affine(n.geom_px, :a, :b, :d, :e, :xoff, :yoff), 4326)) AS geography) " +
                            "WHERE n.map_version_id = :mapVersionId"
                    )
                    .setParameter("a", a)
                    .setParameter("b", b)
                    .setParameter("d", d)
                    .setParameter("e", e)
                    .setParameter("xoff", xoff)
                    .setParameter("yoff", yoff)
                    .setParameter("mapVersionId", mapVersionId)
                    .executeUpdate();

                    entityManager.createNativeQuery(
                            "UPDATE poi p " +
                            "SET geom_wgs84 = CAST(ST_SetSRID(ST_Affine(p.geom_px, :a, :b, :d, :e, :xoff, :yoff), 4326) AS geography) " +
                            "WHERE p.map_version_id = :mapVersionId AND p.geom_wgs84 IS NULL"
                    )
                    .setParameter("a", a)
                    .setParameter("b", b)
                    .setParameter("d", d)
                    .setParameter("e", e)
                    .setParameter("xoff", xoff)
                    .setParameter("yoff", yoff)
                    .setParameter("mapVersionId", mapVersionId)
                    .executeUpdate();

                    entityManager.createNativeQuery(
                            "UPDATE edge e " +
                            "SET geom_wgs84 = CAST(ST_Force3D(ST_SetSRID(ST_Affine(e.geom_px, :a, :b, :d, :e, :xoff, :yoff), 4326)) AS geography) " +
                            "WHERE e.map_version_id = :mapVersionId"
                    )
                    .setParameter("a", a)
                    .setParameter("b", b)
                    .setParameter("d", d)
                    .setParameter("e", e)
                    .setParameter("xoff", xoff)
                    .setParameter("yoff", yoff)
                    .setParameter("mapVersionId", mapVersionId)
                    .executeUpdate();

                    entityManager.createNativeQuery(
                            "UPDATE edge e " +
                            "SET length_m = CAST(ST_Length(e.geom_wgs84) AS numeric(10,2)) " +
                            "WHERE e.map_version_id = :mapVersionId"
                    )
                    .setParameter("mapVersionId", mapVersionId)
                    .executeUpdate();
                }
            }
        }

        List<UUID> floorIds = floors.stream().map(Floor::getId).toList();
        java.util.Map<UUID, Floorplan> currentFloorplansByFloorId = floorIds.isEmpty()
                ? java.util.Map.of()
                : floorplanRepository.findAllByFloorIdInAndIsCurrentTrue(floorIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        floorplan -> floorplan.getFloor().getId(),
                        floorplan -> floorplan,
                        (existing, replacement) -> existing,
                        java.util.LinkedHashMap::new
                ));

        List<UUID> currentFloorplanIds = currentFloorplansByFloorId.values().stream()
                .map(Floorplan::getId)
                .toList();
        java.util.Set<UUID> analyzedFloorplanIds = currentFloorplanIds.isEmpty()
                ? java.util.Set.of()
                : new java.util.LinkedHashSet<>(aiDetectionRepository.findAnalyzedFloorplanIdsByTenantIdAndFloorplanIds(tenantId, currentFloorplanIds));

        List<MapEditorInitBuildingDraftFloorDTO> floorStates = new ArrayList<>();
        int analyzedFloorCount = 0;
        int draftReadyFloorCount = 0;

        for (Floor floor : floors) {
            Floorplan currentFloorplan = currentFloorplansByFloorId.get(floor.getId());
            boolean analyzed = currentFloorplan != null && analyzedFloorplanIds.contains(currentFloorplan.getId());
            FloorDraftContentState contentState = getFloorDraftContentState(draftMapVersion.getId(), floor.getId());

            if (analyzed) {
                analyzedFloorCount++;
            }

            if (contentState.isFullyReady()) {
                draftReadyFloorCount++;
            }

            floorStates.add(MapEditorInitBuildingDraftFloorDTO.of(
                floor,
                currentFloorplan,
                analyzed,
                contentState.isFullyReady(),
                false
            ));
        }

        return MapEditorInitBuildingDraftResponseDTO.of(
                building,
                draftMapVersion,
                false,
                analyzedFloorCount,
                draftReadyFloorCount,
                floorStates
        );
    }

    private List<Double> calculateAffineTransform(List<MapPointPair> pairs) {
        int n = pairs.size();
        if (n < 3) return null;

        double sumX = 0, sumY = 0, sumXX = 0, sumYY = 0, sumXY = 0;
        double sumLon = 0, sumLat = 0;
        double sumXLon = 0, sumYLon = 0;
        double sumXLat = 0, sumYLat = 0;

        for (MapPointPair pair : pairs) {
            double x = pair.pxX();
            double y = pair.pxY();
            double lon = pair.lon();
            double lat = pair.lat();

            sumX += x;
            sumY += y;
            sumXX += x * x;
            sumYY += y * y;
            sumXY += x * y;
            
            sumLon += lon;
            sumLat += lat;
            sumXLon += x * lon;
            sumYLon += y * lon;
            sumXLat += x * lat;
            sumYLat += y * lat;
        }

        double[][] M = {
            {sumXX, sumXY, sumX},
            {sumXY, sumYY, sumY},
            {sumX, sumY, (double) n}
        };

        double det = M[0][0] * (M[1][1] * M[2][2] - M[1][2] * M[2][1])
                   - M[0][1] * (M[1][0] * M[2][2] - M[1][2] * M[2][0])
                   + M[0][2] * (M[1][0] * M[2][1] - M[1][1] * M[2][0]);

        if (Math.abs(det) < 1e-12) {
            return null;
        }

        double[][] adj = {
            {M[1][1] * M[2][2] - M[1][2] * M[2][1], M[0][2] * M[2][1] - M[0][1] * M[2][2], M[0][1] * M[1][2] - M[0][2] * M[1][1]},
            {M[1][2] * M[2][0] - M[1][0] * M[2][2], M[0][0] * M[2][2] - M[0][2] * M[2][0], M[0][2] * M[1][0] - M[0][0] * M[1][2]},
            {M[1][0] * M[2][1] - M[1][1] * M[2][0], M[0][1] * M[2][0] - M[0][0] * M[2][1], M[0][0] * M[1][1] - M[0][1] * M[1][0]}
        };

        double[][] Minv = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                Minv[i][j] = adj[i][j] / det;
            }
        }

        double[] Vlon = {sumXLon, sumYLon, sumLon};
        double a = Minv[0][0] * Vlon[0] + Minv[0][1] * Vlon[1] + Minv[0][2] * Vlon[2];
        double b = Minv[1][0] * Vlon[0] + Minv[1][1] * Vlon[1] + Minv[1][2] * Vlon[2];
        double xoff = Minv[2][0] * Vlon[0] + Minv[2][1] * Vlon[1] + Minv[2][2] * Vlon[2];

        double[] Vlat = {sumXLat, sumYLat, sumLat};
        double d = Minv[0][0] * Vlat[0] + Minv[0][1] * Vlat[1] + Minv[0][2] * Vlat[2];
        double e = Minv[1][0] * Vlat[0] + Minv[1][1] * Vlat[1] + Minv[1][2] * Vlat[2];
        double yoff = Minv[2][0] * Vlat[0] + Minv[2][1] * Vlat[1] + Minv[2][2] * Vlat[2];

        return List.of(a, b, d, e, xoff, yoff);
    }

    public List<MapEditorDraftPoiResponseDTO> getBuildingDraftPois(UUID tenantId, UUID buildingId) {
        Building building = buildingRepository.findByIdAndTenant_Id(buildingId, tenantId)
                .orElseThrow(() -> new BuildingException(BuildingErrorCode.BUILDING_NOT_FOUND));

        MapVersion draftMapVersion = mapVersionRepository
                .findFirstByBuildingIdAndMapTypeAndStatusOrderByCreatedAtDesc(buildingId, MapType.BUILDING, "draft")
                .orElseThrow(() -> new com.insideout.backend.domain.map.exception.MapException(
                        com.insideout.backend.domain.map.exception.MapErrorCode.MAP_VERSION_NOT_FOUND
                ));

        List<Poi> pois = poiRepository.findByMapVersionId(draftMapVersion.getId());

        return pois.stream()
                .map(p -> new MapEditorDraftPoiResponseDTO(
                        p.getId(),
                        p.getName(),
                        p.getCode(),
                        p.getFloor() != null ? p.getFloor().getName() : "-",
                        p.getGeomPx() != null ? p.getGeomPx().getX() : null,
                        p.getGeomPx() != null ? p.getGeomPx().getY() : null,
                        p.getExternalApiId(),
                        p.getGeomWgs84() != null ? p.getGeomWgs84().getY() : null, // latitude (Y)
                        p.getGeomWgs84() != null ? p.getGeomWgs84().getX() : null,  // longitude (X)
                        p.getExternalMappingPlaceName(),
                        p.getExternalMappingAddress(),
                        p.getExternalMappingStatus()
                ))
                .toList();
    }

    @Transactional
    public void saveBuildingPoiMappings(UUID tenantId, UUID buildingId, MapEditorPoiMappingsSaveRequestDTO request) {
        Building building = buildingRepository.findByIdAndTenant_Id(buildingId, tenantId)
                .orElseThrow(() -> new BuildingException(BuildingErrorCode.BUILDING_NOT_FOUND));

        MapVersion draftMapVersion = getDraftMapVersionOrThrow(buildingId);

        GeometryFactory wgsGeometryFactory = new GeometryFactory(new org.locationtech.jts.geom.PrecisionModel(), 4326);

        for (MapEditorPoiMappingRequestDTO mapping : request.mappings()) {
            Poi poi = poiRepository.findById(mapping.poiId())
                    .orElseThrow(() -> new com.insideout.backend.domain.map.exception.MapException(
                            com.insideout.backend.domain.map.exception.MapErrorCode.MAP_VERSION_NOT_FOUND
                    ));

            if (!poi.getTenantId().equals(tenantId) || poi.getMapVersion() == null
                    || !poi.getMapVersion().getId().equals(draftMapVersion.getId())) {
                throw new com.insideout.backend.domain.map.exception.MapException(
                        com.insideout.backend.domain.map.exception.MapErrorCode.MAP_VERSION_NOT_FOUND
                );
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
            poiRepository.save(poi);
        }
    }

    private void validatePublishPreconditions(UUID tenantId, Building building, MapVersion draftMapVersion) {
        validateEntranceCalibrationForPublish(tenantId, building);
        validatePoiExternalMappingsForPublish(draftMapVersion);
    }

    private void validateEntranceCalibrationForPublish(UUID tenantId, Building building) {
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
                .findAllByTenantIdAndBuildingIdOrderByCreatedAtAsc(tenantId, building.getId())
                .stream()
                .map(BuildingEntranceMapping::getCampusGateId)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        if (!mappedGateIds.containsAll(expectedGateIds)) {
            throw new com.insideout.backend.domain.map.exception.MapException(
                    com.insideout.backend.domain.map.exception.MapErrorCode.PUBLISH_REQUIRES_ENTRANCE_CALIBRATION
            );
        }
    }

    private void validatePoiExternalMappingsForPublish(MapVersion draftMapVersion) {
        boolean hasPendingPoi = poiRepository.findByMapVersionId(draftMapVersion.getId()).stream()
                .anyMatch(poi -> !"confirmed".equals(poi.getExternalMappingStatus()) && !"excluded".equals(poi.getExternalMappingStatus()));

        if (hasPendingPoi) {
            throw new com.insideout.backend.domain.map.exception.MapException(
                    com.insideout.backend.domain.map.exception.MapErrorCode.PUBLISH_REQUIRES_POI_EXTERNAL_MAPPING
            );
        }
    }

    private record MapPointPair(double pxX, double pxY, double lon, double lat) {}
}
