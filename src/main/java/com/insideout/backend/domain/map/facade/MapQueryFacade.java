package com.insideout.backend.domain.map.facade;

import com.insideout.backend.domain.building.entity.Floor;
import com.insideout.backend.domain.building.entity.Building;
import com.insideout.backend.domain.building.entity.Campus;
import com.insideout.backend.domain.building.entity.BuildingDirectory;
import com.insideout.backend.domain.building.repository.BuildingRepository;
import com.insideout.backend.domain.building.repository.BuildingDirectoryRepository;
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
import com.insideout.backend.domain.map.storage.MapAssetStorage;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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

    private final NodeRepository nodeRepository;
    private final BuildingDirectoryRepository buildingDirectoryRepository;
    private final BuildingRepository buildingRepository;
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
        Optional<BuildingDirectory> building = destinationBuildingId != null
                ? buildingDirectoryRepository.findByIdAndIsPublicTrue(destinationBuildingId)
                : buildingDirectoryRepository.findNearestPublicBuilding(
                        destinationX,
                        destinationY,
                        REGISTERED_BUILDING_SEARCH_RADIUS_METERS
                );

        return building.flatMap(directory -> nodeRepository
                .findNearestEntranceByBuildingId(directory.getId(), destinationX, destinationY)
                .flatMap(node -> toIndoorDestinationAnchor(directory, node)));
    }

    public Optional<IndoorPoiDestination> findIndoorPoiDestination(Long destinationPoiId) {
        if (destinationPoiId == null) {
            return Optional.empty();
        }

        return poiRepository.findByPublicId(destinationPoiId)
                .filter(poi -> poi.getAnchorNodeId() != null)
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

        return mapVersion.map(version -> toRoutingGraph(mapType, ownerId, version));
    }

    public Optional<String> findCurrentFloorplanImageUrl(UUID floorId) {
        if (floorId == null) {
            return Optional.empty();
        }
        return mapAssetStorage.findCurrentMapAsset(MapType.BUILDING, floorId)
                .map(asset -> asset.imageUrl());
    }

    public Optional<String> findCurrentCampusMapImageUrl(UUID campusId) {
        if (campusId == null) {
            return Optional.empty();
        }
        return mapAssetStorage.findCurrentMapAsset(MapType.CAMPUS, campusId)
                .map(asset -> asset.imageUrl());
    }

    private RoutingGraph toRoutingGraph(MapType mapType, UUID ownerId, MapVersion mapVersion) {
        List<RoutingNode> nodes = nodeRepository.findByMapVersionId(mapVersion.getId()).stream()
                .map(this::toRoutingNode)
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

    private RoutingNode toRoutingNode(Node node) {
        Point point = node.getGeomPx();
        Floor floor = node.getFloor();
        return RoutingNode.builder()
                .id(node.getId())
                .kind(node.getKindCode())
                .name(node.getNameKo())
                .floorId(floor == null ? null : floor.getId())
                .floorName(floor == null ? null : floor.getName())
                .x(point.getX())
                .y(point.getY())
                .build();
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

    private Optional<IndoorDestinationAnchor> toIndoorDestinationAnchor(BuildingDirectory building, Node node) {
        Point point = node.getGeomWgs84();
        if (point == null) {
            return Optional.empty();
        }

        Optional<Building> buildingEntity = buildingRepository.findById(building.getId());
        Campus campus = buildingEntity.map(Building::getCampus).orElse(null);
        Point campusEntrance = campus == null ? null : campus.getPrimaryEntrance();

        return Optional.of(IndoorDestinationAnchor.builder()
                .campusId(campus == null ? null : campus.getId())
                .campusName(campus == null ? null : campus.getName())
                .campusEntranceName(campus == null ? null : campus.getPrimaryEntranceName())
                .campusEntranceX(campusEntrance == null ? null : campusEntrance.getX())
                .campusEntranceY(campusEntrance == null ? null : campusEntrance.getY())
                .buildingId(building.getId())
                .buildingName(building.getName())
                .entranceNodeId(node.getId())
                .entranceName(node.getNameKo())
                .x(point.getX())
                .y(point.getY())
                .build());
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
        return Optional.of(IndoorPoiDestination.builder()
                .publicId(poi.getPublicId())
                .poiId(poi.getId())
                .name(poi.getName())
                .anchorNodeId(poi.getAnchorNodeId())
                .floorId(floor.getId())
                .floorName(floor.getName())
                .buildingId(building.getId())
                .buildingName(building.getName())
                .campusId(campus == null ? null : campus.getId())
                .campusName(campus == null ? null : campus.getName())
                .build());
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

        public double outdoorTargetX() {
            return hasCampus() ? campusEntranceX : x;
        }

        public double outdoorTargetY() {
            return hasCampus() ? campusEntranceY : y;
        }

        public String outdoorTargetName() {
            if (hasCampus() && campusEntranceName != null && !campusEntranceName.isBlank()) {
                return campusEntranceName;
            }
            if (hasCampus()) {
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
            UUID floorId,
            String floorName,
            double x,
            double y
    ) {
        public String displayName() {
            return name == null || name.isBlank() ? kind : name;
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
