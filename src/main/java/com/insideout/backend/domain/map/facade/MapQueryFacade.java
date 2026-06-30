package com.insideout.backend.domain.map.facade;

import com.insideout.backend.domain.building.entity.Floor;
import com.insideout.backend.domain.building.entity.Building;
import com.insideout.backend.domain.building.entity.Campus;
import com.insideout.backend.domain.building.entity.BuildingDirectory;
import com.insideout.backend.domain.building.entity.BuildingEntranceMapping;
import com.insideout.backend.domain.building.repository.BuildingRepository;
import com.insideout.backend.domain.building.repository.BuildingDirectoryRepository;
import com.insideout.backend.domain.building.repository.BuildingEntranceMappingRepository;
import com.insideout.backend.domain.building.repository.FloorRepository;
import com.insideout.backend.domain.map.entity.Edge;
import com.insideout.backend.domain.map.entity.MapVersion;
import com.insideout.backend.domain.map.entity.Node;
import com.insideout.backend.domain.map.entity.Obstacle;
import com.insideout.backend.domain.map.entity.Poi;
import com.insideout.backend.domain.map.entity.VerticalConnector;
import com.insideout.backend.domain.map.entity.VerticalConnectorNode;
import com.insideout.backend.domain.map.enums.MapType;
import com.insideout.backend.domain.map.repository.EdgeRepository;
import com.insideout.backend.domain.map.repository.MapVersionRepository;
import com.insideout.backend.domain.map.repository.NodeRepository;
import com.insideout.backend.domain.map.repository.ObstacleRepository;
import com.insideout.backend.domain.map.repository.PoiRepository;
import com.insideout.backend.domain.map.repository.VerticalConnectorNodeRepository;
import com.insideout.backend.domain.map.storage.MapAssetDescriptor;
import com.insideout.backend.domain.map.storage.MapAssetStorage;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * 타 도메인(예: Navigation)에서 Map 도메인의 데이터를 안전하게 조회하기 위한 읽기 전용 서비스.
 *
 * <p>Repository를 직접 타 도메인에 노출하지 않고,
 * 이 서비스를 통해서 필요한 노드, 엣지, POI 등의 데이터를 제공합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MapQueryFacade {

    private static final double REGISTERED_BUILDING_SEARCH_RADIUS_METERS = 50.0;
    private static final double INSTRUCTION_LANDMARK_RADIUS_PX = 180.0;
    private static final double POI_ANCHOR_SNAP_RADIUS_PX = 150.0;

    private final ConcurrentMap<RoutingGraphCacheKey, RoutingGraph> routingGraphCache = new ConcurrentHashMap<>();

    private final NodeRepository nodeRepository;
    private final BuildingDirectoryRepository buildingDirectoryRepository;
    private final BuildingRepository buildingRepository;
    private final BuildingEntranceMappingRepository buildingEntranceMappingRepository;
    private final FloorRepository floorRepository;
    private final PoiRepository poiRepository;
    private final EdgeRepository edgeRepository;
    private final MapVersionRepository mapVersionRepository;
    private final VerticalConnectorNodeRepository verticalConnectorNodeRepository;
    private final ObstacleRepository obstacleRepository;
    private final MapAssetStorage mapAssetStorage;

    public Optional<IndoorDestinationAnchor> findIndoorDestinationAnchor(
            UUID destinationBuildingId,
            double destinationX,
            double destinationY
    ) {
        return findIndoorDestinationAnchor(
                destinationBuildingId,
                destinationX,
                destinationY,
                destinationX,
                destinationY
        );
    }

    public Optional<IndoorDestinationAnchor> findIndoorDestinationAnchor(
            UUID destinationBuildingId,
            double destinationX,
            double destinationY,
            double outdoorReferenceX,
            double outdoorReferenceY
    ) {
        Optional<BuildingDirectory> building = destinationBuildingId != null
                ? buildingDirectoryRepository.findByIdAndIsPublicTrue(destinationBuildingId)
                : buildingDirectoryRepository.findNearestPublicBuilding(
                        destinationX,
                        destinationY,
                        REGISTERED_BUILDING_SEARCH_RADIUS_METERS
                );

        return building.flatMap(directory -> findMappedEntranceAnchor(directory, outdoorReferenceX, outdoorReferenceY)
                .or(() -> nodeRepository
                        .findNearestEntranceByBuildingId(directory.getId(), destinationX, destinationY)
                        .flatMap(node -> toIndoorDestinationAnchor(directory, node))));
    }

    public Optional<IndoorPoiDestination> findIndoorPoiDestination(Long destinationPoiId) {
        if (destinationPoiId == null) {
            return Optional.empty();
        }

        return poiRepository.findPublishedByPublicId(destinationPoiId)
                .flatMap(this::toIndoorPoiDestination);
    }

    public Optional<UUID> findNearestPublishedCampusNodeId(UUID campusId, double x, double y) {
        if (campusId == null) {
            return Optional.empty();
        }
        return nodeRepository.findNearestPublishedCampusNode(campusId, x, y).map(Node::getId);
    }

    public Optional<RoutingGraph> findPublishedRoutingGraph(MapType mapType, UUID ownerId) {
        Optional<MapVersion> mapVersion = switch (mapType) {
            case CAMPUS -> mapVersionRepository.findFirstByCampusIdAndMapTypeAndStatusOrderByCreatedAtDesc(
                    ownerId,
                    MapType.CAMPUS,
                    "published"
            );
            case BUILDING -> mapVersionRepository.findFirstByBuildingIdAndMapTypeAndStatusOrderByCreatedAtDesc(
                    ownerId,
                    MapType.BUILDING,
                    "published"
            );
        };

        if (mapVersion.isEmpty()) {
            return Optional.empty();
        }

        MapVersion version = mapVersion.get();
        evictStaleRoutingGraphCacheEntries(mapType, ownerId, version.getId());
        RoutingGraphCacheKey cacheKey = new RoutingGraphCacheKey(mapType, ownerId, version.getId());
        return Optional.of(routingGraphCache.computeIfAbsent(
                cacheKey,
                key -> toRoutingGraph(mapType, ownerId, version)
        ));
    }

    public void evictPublishedRoutingGraphCache(MapType mapType, UUID ownerId) {
        routingGraphCache.keySet().removeIf(key -> key.matches(mapType, ownerId));
    }

    private void evictStaleRoutingGraphCacheEntries(MapType mapType, UUID ownerId, UUID currentMapVersionId) {
        routingGraphCache.keySet().removeIf(key ->
                key.matches(mapType, ownerId) && !Objects.equals(key.mapVersionId(), currentMapVersionId)
        );
    }

    public Optional<String> findCurrentFloorplanImageUrl(UUID floorId) {
        if (floorId == null) {
            return Optional.empty();
        }
        return mapAssetStorage.findCurrentMapAsset(MapType.BUILDING, floorId)
                .map(asset -> asset.imageUrl());
    }

    public List<PublishedFloorplan> findCurrentBuildingFloorplans(UUID buildingId) {
        if (buildingId == null) {
            return List.of();
        }

        Optional<MapVersion> publishedVersion = mapVersionRepository.findFirstByBuildingIdAndMapTypeAndStatusOrderByCreatedAtDesc(
                buildingId,
                MapType.BUILDING,
                "published"
        );
        if (publishedVersion.isEmpty()) {
            return List.of();
        }

        Set<UUID> publishedFloorIds = nodeRepository.findByMapVersionId(publishedVersion.get().getId()).stream()
                .map(Node::getFloor)
                .filter(Objects::nonNull)
                .map(Floor::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (publishedFloorIds.isEmpty()) {
            return List.of();
        }

        return floorRepository.findAllByBuilding_IdOrderByLevelDesc(buildingId).stream()
                .filter(floor -> publishedFloorIds.contains(floor.getId()))
                .map(floor -> mapAssetStorage.findCurrentMapAsset(MapType.BUILDING, floor.getId())
                        .map(asset -> toPublishedFloorplan(floor, asset))
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    private PublishedFloorplan toPublishedFloorplan(Floor floor, MapAssetDescriptor asset) {
        return new PublishedFloorplan(
                floor.getId(),
                floor.getName(),
                asset.imageUrl()
        );
    }

    public Optional<String> findCurrentCampusMapImageUrl(UUID campusId) {
        if (campusId == null) {
            return Optional.empty();
        }
        return mapAssetStorage.findCurrentMapAsset(MapType.CAMPUS, campusId)
                .map(asset -> asset.imageUrl());
    }

    private RoutingGraph toRoutingGraph(MapType mapType, UUID ownerId, MapVersion mapVersion) {
        List<Poi> instructionLandmarks = mapType == MapType.BUILDING
                ? poiRepository.findByMapVersionId(mapVersion.getId()).stream()
                        .filter(this::isInstructionLandmark)
                        .toList()
                : List.of();
        List<RoutingNode> nodes = nodeRepository.findByMapVersionId(mapVersion.getId()).stream()
                .map(node -> toRoutingNode(node, instructionLandmarks))
                .toList();
        List<RoutingEdge> edges = edgeRepository.findByMapVersionId(mapVersion.getId()).stream()
                .map(this::toRoutingEdge)
                .toList();
        List<VerticalRoutingLink> verticalLinks = verticalConnectorNodeRepository
                .findByConnectorMapVersionId(mapVersion.getId())
                .stream()
                .collect(Collectors.groupingBy(link -> link.getConnector().getId()))
                .values()
                .stream()
                .flatMap(this::toVerticalRoutingLinks)
                .toList();

        return RoutingGraph.builder()
                .mapVersionId(mapVersion.getId())
                .mapType(mapType)
                .mapImageUrl(resolveMapImageUrl(mapType, ownerId))
                .nodes(nodes)
                .edges(edges)
                .verticalLinks(verticalLinks)
                .obstacles(findActiveObstacles(mapType, ownerId))
                .build();
    }

    private RoutingNode toRoutingNode(Node node, List<Poi> instructionLandmarks) {
        Point point = node.getGeomPx();
        Floor floor = node.getFloor();
        return RoutingNode.builder()
                .id(node.getId())
                .kind(node.getKindCode())
                .name(node.getNameKo())
                .landmarkName(findNearestLandmarkName(node, instructionLandmarks))
                .floorId(floor == null ? null : floor.getId())
                .floorName(floor == null ? null : floor.getName())
                .x(point.getX())
                .y(point.getY())
                .build();
    }

    private String findNearestLandmarkName(Node node, List<Poi> instructionLandmarks) {
        Point point = node.getGeomPx();
        Floor floor = node.getFloor();
        if (point == null || floor == null || instructionLandmarks.isEmpty()) {
            return null;
        }

        return instructionLandmarks.stream()
                .filter(poi -> poi.getFloor() != null && Objects.equals(poi.getFloor().getId(), floor.getId()))
                .filter(poi -> poi.getGeomPx() != null)
                .min(Comparator.comparingDouble(poi -> landmarkDistance(node, poi)))
                .filter(poi -> Objects.equals(poi.getAnchorNodeId(), node.getId())
                        || point.distance(poi.getGeomPx()) <= INSTRUCTION_LANDMARK_RADIUS_PX)
                .map(Poi::getName)
                .orElse(null);
    }

    private double landmarkDistance(Node node, Poi poi) {
        if (Objects.equals(poi.getAnchorNodeId(), node.getId())) {
            return 0.0;
        }
        return node.getGeomPx().distance(poi.getGeomPx());
    }

    private boolean isInstructionLandmark(Poi poi) {
        String name = poi.getName();
        if (name == null || name.isBlank()) {
            return false;
        }

        String normalized = name.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        return !(normalized.contains("화장실")
                || normalized.contains("엘리베이터")
                || normalized.contains("에스컬레이터")
                || normalized.contains("계단")
                || normalized.contains("restroom")
                || normalized.contains("toilet")
                || normalized.contains("elevator")
                || normalized.contains("escalator")
                || normalized.contains("stair")
                || normalized.contains("gate")
                || normalized.contains("openingsoon")
                || normalized.contains("openningsoon"));
    }

    private RoutingEdge toRoutingEdge(Edge edge) {
        return RoutingEdge.builder()
                .id(edge.getId())
                .fromNodeId(edge.getFromNode().getId())
                .toNodeId(edge.getToNode().getId())
                .kind(edge.getKindCode())
                .directed(edge.isDirected())
                .length(decimalToDouble(edge.getLengthM()))
                .baseWeight(decimalToDouble(edge.getBaseWeight()))
                .build();
    }

    private java.util.stream.Stream<VerticalRoutingLink> toVerticalRoutingLinks(List<VerticalConnectorNode> nodes) {
        return nodes.stream()
                .flatMap(from -> nodes.stream()
                        .filter(to -> !from.getNode().getId().equals(to.getNode().getId()))
                        .map(to -> toVerticalRoutingLink(from, to)));
    }

    private VerticalRoutingLink toVerticalRoutingLink(VerticalConnectorNode from, VerticalConnectorNode to) {
        VerticalConnector connector = from.getConnector();
        return VerticalRoutingLink.builder()
                .fromNodeId(from.getNode().getId())
                .toNodeId(to.getNode().getId())
                .connectorKind(connector.getKind())
                .connectorName(connector.getName())
                .fromFloorName(from.getFloor().getName())
                .toFloorName(to.getFloor().getName())
                .avgWaitSeconds(connector.getAvgWaitSeconds())
                .build();
    }

    private String resolveMapImageUrl(MapType mapType, UUID ownerId) {
        if (mapType == MapType.CAMPUS) {
            return mapAssetStorage.findCurrentMapAsset(MapType.CAMPUS, ownerId)
                    .map(asset -> asset.imageUrl())
                    .orElse(null);
        }

        return null;
    }

    private List<RoutingObstacle> findActiveObstacles(MapType mapType, UUID ownerId) {
        List<Obstacle> obstacles = switch (mapType) {
            case CAMPUS -> obstacleRepository.findActiveByCampusId(ownerId);
            case BUILDING -> obstacleRepository.findActiveByBuildingId(ownerId);
        };

        return obstacles.stream()
                .map(this::toRoutingObstacle)
                .toList();
    }

    private RoutingObstacle toRoutingObstacle(Obstacle obstacle) {
        return RoutingObstacle.builder()
                .affectedEdgeIds(obstacle.getAffectedEdgeIds() == null ? List.of() : obstacle.getAffectedEdgeIds())
                .extraCost(decimalToDouble(obstacle.getExtraCost()))
                .blocking(obstacle.isBlocking())
                .build();
    }

    private double decimalToDouble(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    private Optional<IndoorDestinationAnchor> findMappedEntranceAnchor(
            BuildingDirectory directory,
            double outdoorReferenceX,
            double outdoorReferenceY
    ) {
        if (directory.getPublishedVersion() == null) {
            return Optional.empty();
        }

        Optional<Building> buildingEntity = buildingRepository.findById(directory.getId());
        Campus campus = buildingEntity.map(Building::getCampus).orElse(null);
        if (campus == null) {
            return Optional.empty();
        }

        Map<String, CampusGate> gatesById = extractCampusGates(campus).stream()
                .collect(Collectors.toMap(CampusGate::id, gate -> gate, (first, ignored) -> first));
        if (gatesById.isEmpty()) {
            return Optional.empty();
        }

        List<BuildingEntranceMapping> mappings = buildingEntranceMappingRepository
                .findAllByTenantIdAndBuildingIdAndMapVersionIdOrderByCreatedAtAsc(
                        directory.getTenant().getId(),
                        directory.getId(),
                        directory.getPublishedVersion().getId()
                );

        return mappings.stream()
                .map(mapping -> {
                    CampusGate gate = gatesById.get(mapping.getCampusGateId());
                    if (gate == null) {
                        return null;
                    }
                    return new MappedEntranceCandidate(
                            mapping,
                            gate,
                            geographicDistanceMeters(outdoorReferenceX, outdoorReferenceY, gate.x(), gate.y())
                    );
                })
                .filter(Objects::nonNull)
                .min(Comparator.comparingDouble(MappedEntranceCandidate::distance))
                .flatMap(candidate -> nodeRepository.findById(candidate.mapping().getEntranceNodeId())
                        .flatMap(node -> toIndoorDestinationAnchor(directory, node, campus, candidate.gate())));
    }

    private boolean hasPublishedCampusMap(Campus campus) {
        return campus != null && mapVersionRepository
                .findFirstByCampusIdAndMapTypeAndStatusOrderByCreatedAtDesc(
                        campus.getId(),
                        MapType.CAMPUS,
                        "published"
                )
                .isPresent();
    }

    private List<CampusGate> extractCampusGates(Campus campus) {
        Object gatesValue = campus.getMeta() == null ? null : campus.getMeta().get("gates");
        if (!(gatesValue instanceof List<?> gates)) {
            return List.of();
        }

        return gates.stream()
                .map(this::toCampusGate)
                .flatMap(Optional::stream)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private Optional<CampusGate> toCampusGate(Object value) {
        if (!(value instanceof Map<?, ?> gate)) {
            return Optional.empty();
        }

        Object locationValue = gate.get("location");
        if (!(locationValue instanceof Map<?, ?> location)) {
            return Optional.empty();
        }

        String id = asString(gate.get("id"));
        String name = asString(gate.get("name"));
        Double x = asDouble(location.get("longitude"));
        Double y = asDouble(location.get("latitude"));
        if (id == null || id.isBlank() || x == null || y == null) {
            return Optional.empty();
        }

        return Optional.of(new CampusGate(id, name, x, y));
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private double geographicDistanceMeters(double firstX, double firstY, double secondX, double secondY) {
        double lat1 = Math.toRadians(firstY);
        double lat2 = Math.toRadians(secondY);
        double dLat = Math.toRadians(secondY - firstY);
        double dLon = Math.toRadians(secondX - firstX);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6_371_000.0 * c;
    }

    private Optional<IndoorDestinationAnchor> toIndoorDestinationAnchor(BuildingDirectory building, Node node) {
        Point point = node.getGeomWgs84();
        if (point == null) {
            return Optional.empty();
        }

        Optional<Building> buildingEntity = buildingRepository.findById(building.getId());
        Campus campus = buildingEntity.map(Building::getCampus).orElse(null);
        boolean hasPublishedCampusMap = hasPublishedCampusMap(campus);
        Point campusEntrance = campus == null ? null : campus.getPrimaryEntrance();

        return Optional.of(IndoorDestinationAnchor.builder()
                .campusId(hasPublishedCampusMap ? campus.getId() : null)
                .campusName(hasPublishedCampusMap ? campus.getName() : null)
                .campusEntranceName(hasPublishedCampusMap ? campus.getPrimaryEntranceName() : null)
                .campusEntranceX(hasPublishedCampusMap && campusEntrance != null ? campusEntrance.getX() : null)
                .campusEntranceY(hasPublishedCampusMap && campusEntrance != null ? campusEntrance.getY() : null)
                .buildingId(building.getId())
                .buildingName(building.getName())
                .entranceNodeId(node.getId())
                .entranceName(node.getNameKo())
                .x(point.getX())
                .y(point.getY())
                .build());
    }

    private Optional<IndoorDestinationAnchor> toIndoorDestinationAnchor(
            BuildingDirectory building,
            Node node,
            Campus campus,
            CampusGate gate
    ) {
        Point point = node.getGeomWgs84();
        if (point == null) {
            return Optional.empty();
        }

        return Optional.of(IndoorDestinationAnchor.builder()
                .campusId(hasPublishedCampusMap(campus) ? campus.getId() : null)
                .campusName(hasPublishedCampusMap(campus) ? campus.getName() : null)
                .campusEntranceName(gate.name())
                .campusEntranceX(gate.x())
                .campusEntranceY(gate.y())
                .buildingId(building.getId())
                .buildingName(building.getName())
                .entranceNodeId(node.getId())
                .entranceName(defaultText(node.getNameKo(), gate.name()))
                .x(point.getX())
                .y(point.getY())
                .build());
    }

    private String defaultText(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private Optional<IndoorPoiDestination> toIndoorPoiDestination(Poi poi) {
        Floor floor = poi.getFloor();
        if (floor == null) {
            return Optional.empty();
        }

        Building building = floor.getBuilding();
        if (building == null) {
            return Optional.empty();
        }

        Campus campus = building.getCampus();
        UUID anchorNodeId = resolvePoiAnchorNodeId(poi).orElse(null);
        if (anchorNodeId == null) {
            return Optional.empty();
        }

        return Optional.of(IndoorPoiDestination.builder()
                .publicId(poi.getPublicId())
                .poiId(poi.getId())
                .name(poi.getName())
                .anchorNodeId(anchorNodeId)
                .floorId(floor.getId())
                .floorName(floor.getName())
                .buildingId(building.getId())
                .buildingName(building.getName())
                .campusId(campus == null ? null : campus.getId())
                .campusName(campus == null ? null : campus.getName())
                .build());
    }

    private Optional<UUID> resolvePoiAnchorNodeId(Poi poi) {
        if (poi.getAnchorNodeId() != null) {
            return Optional.of(poi.getAnchorNodeId());
        }
        if (poi.getMapVersion() == null
                || poi.getMapVersion().getId() == null
                || poi.getFloor() == null
                || poi.getFloor().getId() == null
                || poi.getGeomPx() == null) {
            return Optional.empty();
        }

        Point geomPx = poi.getGeomPx();
        return nodeRepository.findNearestRoutableNodeByPixel(
                        poi.getMapVersion().getId(),
                        poi.getFloor().getId(),
                        geomPx.getX(),
                        geomPx.getY()
                )
                .filter(node -> node.getGeomPx() != null
                        && geomPx.distance(node.getGeomPx()) <= POI_ANCHOR_SNAP_RADIUS_PX)
                .map(Node::getId);
    }

    @Builder
    public record IndoorDestinationAnchor(
            UUID campusId,
            String campusName,
            String campusEntranceName,
            Double campusEntranceX,
            Double campusEntranceY,
            UUID buildingId,
            String buildingName,
            UUID entranceNodeId,
            String entranceName,
            double x,
            double y
    ) {
        public IndoorDestinationAnchor(
                UUID buildingId,
                String buildingName,
                UUID entranceNodeId,
                String entranceName,
                double x,
                double y
        ) {
            this(null, null, null, null, null, buildingId, buildingName, entranceNodeId, entranceName, x, y);
        }

        public boolean hasCampus() {
            return campusId != null && campusEntranceX != null && campusEntranceY != null;
        }

        public boolean hasOutdoorGate() {
            return campusEntranceX != null && campusEntranceY != null;
        }

        public double outdoorTargetX() {
            return hasOutdoorGate() ? campusEntranceX : x;
        }

        public double outdoorTargetY() {
            return hasOutdoorGate() ? campusEntranceY : y;
        }

        public String outdoorTargetName() {
            if (hasOutdoorGate() && campusEntranceName != null && !campusEntranceName.isBlank()) {
                return campusEntranceName;
            }
            if (hasOutdoorGate() && campusName != null && !campusName.isBlank()) {
                return campusName;
            }
            if (entranceName != null && !entranceName.isBlank()) {
                return entranceName;
            }
            return buildingName;
        }
    }

    @Builder
    public record IndoorPoiDestination(
            Long publicId,
            UUID poiId,
            String name,
            UUID anchorNodeId,
            UUID floorId,
            String floorName,
            UUID buildingId,
            String buildingName,
            UUID campusId,
            String campusName
    ) {
        public boolean hasCampus() {
            return campusId != null;
        }
    }

    public record PublishedFloorplan(
            UUID floorId,
            String floorName,
            String mapImageUrl
    ) {
    }

    private record CampusGate(
            String id,
            String name,
            double x,
            double y
    ) {
    }

    private record MappedEntranceCandidate(
            BuildingEntranceMapping mapping,
            CampusGate gate,
            double distance
    ) {
    }

    private record RoutingGraphCacheKey(
            MapType mapType,
            UUID ownerId,
            UUID mapVersionId
    ) {
        private boolean matches(MapType targetMapType, UUID targetOwnerId) {
            return mapType == targetMapType && Objects.equals(ownerId, targetOwnerId);
        }
    }

    @Builder
    public record RoutingGraph(
            UUID mapVersionId,
            MapType mapType,
            String mapImageUrl,
            List<RoutingNode> nodes,
            List<RoutingEdge> edges,
            List<VerticalRoutingLink> verticalLinks,
            List<RoutingObstacle> obstacles
    ) {
        public Map<UUID, RoutingNode> nodeIndex() {
            return nodes.stream().collect(Collectors.toMap(RoutingNode::id, node -> node));
        }
    }

    @Builder
    public record RoutingNode(
            UUID id,
            String kind,
            String name,
            String landmarkName,
            UUID floorId,
            String floorName,
            double x,
            double y
    ) {
        public String displayName() {
            if (hasText(name) && !isGenericNodeName(name, kind)) {
                return name;
            }
            if (hasText(landmarkName)) {
                return landmarkName;
            }
            return localizedKind(kind);
        }

        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }

        private static boolean isGenericNodeName(String value, String kind) {
            String normalized = value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
            String normalizedKind = kind == null ? "" : kind.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
            return normalized.equals(normalizedKind)
                    || normalized.equals("corridor")
                    || normalized.equals("node")
                    || normalized.equals("point")
                    || normalized.equals("복도")
                    || normalized.equals("통로");
        }

        private static String localizedKind(String kind) {
            return switch (kind == null ? "" : kind.toLowerCase(Locale.ROOT)) {
                case "entrance" -> "출입구";
                case "elevator" -> "엘리베이터";
                case "escalator" -> "에스컬레이터";
                case "stair" -> "계단";
                case "ramp" -> "경사로";
                case "corridor" -> "통로";
                default -> "이 지점";
            };
        }
    }

    @Builder
    public record RoutingEdge(
            UUID id,
            UUID fromNodeId,
            UUID toNodeId,
            String kind,
            boolean directed,
            double length,
            double baseWeight
    ) {
    }

    @Builder
    public record RoutingObstacle(
            List<UUID> affectedEdgeIds,
            double extraCost,
            boolean blocking
    ) {
    }

    @Builder
    public record VerticalRoutingLink(
            UUID fromNodeId,
            UUID toNodeId,
            String connectorKind,
            String connectorName,
            String fromFloorName,
            String toFloorName,
            Integer avgWaitSeconds
    ) {
        public String displayName() {
            return connectorName == null || connectorName.isBlank() ? connectorKind : connectorName;
        }
    }
}
