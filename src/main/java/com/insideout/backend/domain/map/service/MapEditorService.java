package com.insideout.backend.domain.map.service;

import com.insideout.backend.domain.ai.dto.response.DetectionViewDTO;
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
import com.insideout.backend.domain.map.dto.response.MapEditorEdgeDTO;
import com.insideout.backend.domain.map.dto.response.MapEditorFloorplanObjectDTO;
import com.insideout.backend.domain.map.dto.response.MapEditorInitBuildingDraftFloorDTO;
import com.insideout.backend.domain.map.dto.response.MapEditorInitBuildingDraftResponseDTO;
import com.insideout.backend.domain.map.dto.response.MapEditorInitResponseDTO;
import com.insideout.backend.domain.map.dto.response.MapEditorNodeDTO;
import com.insideout.backend.domain.map.dto.response.MapEditorPoiDTO;
import com.insideout.backend.domain.map.dto.response.MapEditorZoneDTO;
import com.insideout.backend.domain.map.entity.Edge;
import com.insideout.backend.domain.map.entity.FloorplanObject;
import com.insideout.backend.domain.map.entity.MapVersion;
import com.insideout.backend.domain.map.entity.Node;
import com.insideout.backend.domain.map.entity.Poi;
import com.insideout.backend.domain.map.entity.PoiCategory;
import com.insideout.backend.domain.map.entity.Zone;
import com.insideout.backend.domain.map.entity.ZoneKind;
import com.insideout.backend.domain.map.enums.MapType;
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
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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

        return MapEditorInitResponseDTO.of(
                building,
                floor,
                currentFloorplan,
                floorplanImageUrl,
                draftMapVersion,
                draftCreated,
                initializedFromAi,
                aiDetections.stream().map(DetectionViewDTO::from).toList(),
                nodeRepository.findByMapVersionIdAndFloorId(draftMapVersion.getId(), floorId).stream()
                        .map(MapEditorNodeDTO::from)
                        .toList(),
                edgeRepository.findByMapVersionIdAndFloorId(draftMapVersion.getId(), floorId).stream()
                        .map(MapEditorEdgeDTO::from)
                        .toList(),
                poiRepository.findByMapVersionIdAndFloorId(draftMapVersion.getId(), floorId).stream()
                        .map(MapEditorPoiDTO::from)
                        .toList(),
                zoneRepository.findByMapVersionIdAndFloorId(draftMapVersion.getId(), floorId).stream()
                        .map(MapEditorZoneDTO::from)
                        .toList(),
                floorplanObjectRepository.findByMapVersionIdAndFloorId(draftMapVersion.getId(), floorId).stream()
                        .map(MapEditorFloorplanObjectDTO::from)
                        .toList()
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
        UUID parentVersionId = mapVersionRepository
                .findFirstByBuildingIdAndMapTypeAndStatusOrderByCreatedAtDesc(
                        building.getId(),
                        MapType.BUILDING,
                        "published"
                )
                .map(MapVersion::getId)
                .orElse(null);

        return mapVersionRepository.save(
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

        return Optional.of(
                Poi.builder()
                        .tenantId(mapVersion.getTenantId())
                        .mapVersion(mapVersion)
                        .floor(floor)
                        .categoryId(categoryId)
                        .name(resolvePoiName(detection))
                        .geomPx(point)
                        .footprintPx(footprint)
                        .attrs(buildDetectionProperties(detection))
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

    private String resolveNodeKindCode(String detectType) {
        return switch (normalizeDetectType(detectType)) {
            case "entrance" -> "entrance";
            case "stair" -> "stair";
            case "elevator" -> "elevator";
            case "escalator" -> "escalator";
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

    private LineString toLineString(Geometry geometry) {
        if (geometry instanceof LineString lineString) {
            return lineString;
        }
        return null;
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
}
