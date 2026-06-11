package com.insideout.backend.domain.map.service;

import com.insideout.backend.domain.map.exception.MapErrorCode;
import com.insideout.backend.domain.map.exception.MapException;
import com.insideout.backend.domain.building.entity.BuildingEntranceMapping;
import com.insideout.backend.domain.building.entity.Floor;
import com.insideout.backend.domain.map.dto.request.MapEditorDraftEdgeSaveDTO;
import com.insideout.backend.domain.map.dto.request.MapEditorDraftNodeSaveDTO;
import com.insideout.backend.domain.map.dto.request.MapEditorDraftPoiSaveDTO;
import com.insideout.backend.domain.map.dto.request.MapEditorDraftSaveRequestDTO;
import com.insideout.backend.domain.map.dto.request.MapEditorDraftZoneSaveDTO;
import com.insideout.backend.domain.map.entity.Edge;
import com.insideout.backend.domain.map.entity.MapVersion;
import com.insideout.backend.domain.map.entity.Node;
import com.insideout.backend.domain.map.entity.Poi;
import com.insideout.backend.domain.map.entity.PoiCategory;
import com.insideout.backend.domain.map.entity.VerticalConnectorNode;
import com.insideout.backend.domain.map.entity.Zone;
import com.insideout.backend.domain.map.entity.ZoneKind;
import com.insideout.backend.domain.map.repository.EdgeRepository;
import com.insideout.backend.domain.map.repository.NodeRepository;
import com.insideout.backend.domain.map.repository.PoiCategoryRepository;
import com.insideout.backend.domain.map.repository.PoiRepository;
import com.insideout.backend.domain.map.repository.VerticalConnectorNodeRepository;
import com.insideout.backend.domain.map.repository.ZoneRepository;
import com.insideout.backend.domain.building.repository.BuildingEntranceMappingRepository;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MapEditorDraftPersistenceService {

    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new org.locationtech.jts.geom.PrecisionModel(), 0);

    private final BuildingEntranceMappingRepository buildingEntranceMappingRepository;
    private final VerticalConnectorNodeRepository verticalConnectorNodeRepository;
    private final NodeRepository nodeRepository;
    private final ZoneRepository zoneRepository;
    private final PoiRepository poiRepository;
    private final EdgeRepository edgeRepository;
    private final PoiCategoryRepository poiCategoryRepository;

    public void replaceFloorDraftContent(
            UUID tenantId,
            UUID buildingId,
            MapVersion draftMapVersion,
            Floor floor,
            MapEditorDraftSaveRequestDTO request
    ) {
        List<UUID> existingFloorNodeIds = nodeRepository.findByMapVersionIdAndFloorId(draftMapVersion.getId(), floor.getId()).stream()
                .map(Node::getId)
                .toList();
        Map<UUID, Poi> existingPoisById = poiRepository.findByMapVersionIdAndFloorId(draftMapVersion.getId(), floor.getId()).stream()
                .collect(java.util.stream.Collectors.toMap(
                        Poi::getId,
                        java.util.function.Function.identity(),
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ));

        List<BuildingEntranceMapping> floorMappings = buildingEntranceMappingRepository
                .findAllByTenantIdAndBuildingIdAndMapVersionIdOrderByCreatedAtAsc(tenantId, buildingId, draftMapVersion.getId()).stream()
                .filter(mapping -> existingFloorNodeIds.contains(mapping.getEntranceNodeId()))
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

        edgeRepository.deleteByMapVersionIdAndFloorId(draftMapVersion.getId(), floor.getId());
        poiRepository.deleteByMapVersionIdAndFloorId(draftMapVersion.getId(), floor.getId());
        zoneRepository.deleteByMapVersionIdAndFloorId(draftMapVersion.getId(), floor.getId());
        nodeRepository.deleteByMapVersionIdAndFloorId(draftMapVersion.getId(), floor.getId());

        Map<UUID, Node> savedNodesByRequestId = saveDraftNodes(draftMapVersion, floor, request.nodes());
        saveDraftZones(draftMapVersion, floor, request.zones());
        saveDraftPois(draftMapVersion, floor, request.pois(), savedNodesByRequestId, existingPoisById);
        saveDraftEdges(draftMapVersion, request.edges(), savedNodesByRequestId);
        syncEntranceMappingsAfterFloorDraftSave(savedNodesByRequestId, floorMappings);
        syncVerticalConnectorNodesAfterFloorDraftSave(savedNodesByRequestId, floorConnectorNodes);
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
            zoneRepository.saveAll(ZoneDedupUtil.suppressDuplicateRoomZones(zonesToSave));
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
                            .mapVersion(cachedMapping.getMapVersion())
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

    private Point toPoint(Map<String, Object> geometry) {
        Geometry parsedGeometry = toGeometry(geometry);
        return parsedGeometry instanceof Point point ? point : null;
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
            throw new MapException(MapErrorCode.MAP_INVALID_GEOMETRY);
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
            throw new MapException(MapErrorCode.MAP_INVALID_GEOMETRY);
        }
        return coordinates;
    }

    private Coordinate toCoordinate(Object value) {
        List<?> pair = asList(value);
        if (pair.size() < 2 || !(pair.get(0) instanceof Number x) || !(pair.get(1) instanceof Number y)) {
            throw new MapException(MapErrorCode.MAP_INVALID_GEOMETRY);
        }
        return new Coordinate(x.doubleValue(), y.doubleValue());
    }

    private List<?> asList(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        throw new MapException(MapErrorCode.MAP_INVALID_GEOMETRY);
    }

    private Node resolveSavedNode(UUID requestNodeId, Map<UUID, Node> savedNodesByRequestId) {
        return requestNodeId != null ? savedNodesByRequestId.get(requestNodeId) : null;
    }

    private UUID resolveSavedNodeId(UUID requestNodeId, Map<UUID, Node> savedNodesByRequestId) {
        Node savedNode = resolveSavedNode(requestNodeId, savedNodesByRequestId);
        return savedNode != null ? savedNode.getId() : null;
    }
}
