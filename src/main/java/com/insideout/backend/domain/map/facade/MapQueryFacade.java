package com.insideout.backend.domain.map.facade;

import com.insideout.backend.domain.building.entity.CampusMap;
import com.insideout.backend.domain.building.entity.Floor;
import com.insideout.backend.domain.building.entity.Floorplan;
import com.insideout.backend.domain.building.entity.Building;
import com.insideout.backend.domain.building.entity.Campus;
import com.insideout.backend.domain.building.entity.BuildingDirectory;
import com.insideout.backend.domain.building.repository.CampusMapRepository;
import com.insideout.backend.domain.building.repository.FloorplanRepository;
import com.insideout.backend.domain.building.repository.BuildingRepository;
import com.insideout.backend.domain.building.repository.BuildingDirectoryRepository;
import com.insideout.backend.domain.map.entity.Edge;
import com.insideout.backend.domain.map.entity.MapType;
import com.insideout.backend.domain.map.entity.MapVersion;
import com.insideout.backend.domain.map.entity.Node;
import com.insideout.backend.domain.map.entity.Obstacle;
import com.insideout.backend.domain.map.entity.Poi;
import com.insideout.backend.domain.map.entity.VerticalConnector;
import com.insideout.backend.domain.map.entity.VerticalConnectorNode;
import com.insideout.backend.domain.map.repository.EdgeRepository;
import com.insideout.backend.domain.map.repository.MapVersionRepository;
import com.insideout.backend.domain.map.repository.NodeRepository;
import com.insideout.backend.domain.map.repository.ObstacleRepository;
import com.insideout.backend.domain.map.repository.PoiRepository;
import com.insideout.backend.domain.map.repository.VerticalConnectorNodeRepository;
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

    // 내부적으로 자기 도메인의 Repository는 자유롭게 주입받아 사용합니다.
    private final NodeRepository nodeRepository;
    private final BuildingDirectoryRepository buildingDirectoryRepository;
    private final BuildingRepository buildingRepository;
    private final PoiRepository poiRepository;
    private final EdgeRepository edgeRepository;
    private final MapVersionRepository mapVersionRepository;
    private final VerticalConnectorNodeRepository verticalConnectorNodeRepository;
    private final ObstacleRepository obstacleRepository;
    private final FloorplanRepository floorplanRepository;
    private final CampusMapRepository campusMapRepository;

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
                .map(poi -> {
                    Floor floor = poi.getFloor();
                    Building building = floor.getBuilding();
                    Campus campus = building.getCampus();
                    return new IndoorPoiDestination(
                            poi.getPublicId(),
                            poi.getId(),
                            poi.getName(),
                            poi.getAnchorNodeId(),
                            floor.getId(),
                            floor.getName(),
                            building.getId(),
                            building.getName(),
                            campus == null ? null : campus.getId(),
                            campus == null ? null : campus.getName()
                    );
                });
    }

    public Optional<UUID> findNearestPublishedCampusNodeId(UUID campusId, double x, double y) {
        if (campusId == null) {
            return Optional.empty();
        }
        return nodeRepository.findNearestPublishedCampusNode(campusId, x, y).map(Node::getId);
    }

    public Optional<RoutingGraph> findPublishedRoutingGraph(MapType mapType, UUID ownerId) {
        Optional<MapVersion> mapVersion = switch (mapType) {
            case CAMPUS -> mapVersionRepository.findFirstByCampusIdAndMapTypeAndStatus(ownerId, MapType.CAMPUS, "published");
            case BUILDING -> mapVersionRepository.findFirstByBuildingIdAndMapTypeAndStatus(ownerId, MapType.BUILDING, "published");
        };

        return mapVersion.map(version -> toRoutingGraph(mapType, ownerId, version));
    }

    public Optional<String> findCurrentFloorplanImageUrl(UUID floorId) {
        if (floorId == null) {
            return Optional.empty();
        }
        return floorplanRepository.findByFloorIdAndIsCurrentTrue(floorId).map(Floorplan::getImageUrl);
    }

    public Optional<String> findCurrentCampusMapImageUrl(UUID campusId) {
        if (campusId == null) {
            return Optional.empty();
        }
        return campusMapRepository.findByCampusIdAndIsCurrentTrue(campusId).map(CampusMap::getImageUrl);
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

        return new RoutingGraph(
                mapVersion.getId(),
                mapType,
                resolveMapImageUrl(mapType, ownerId),
                nodes,
                edges,
                verticalLinks,
                findActiveObstacles(mapType, ownerId)
        );
    }

    private RoutingNode toRoutingNode(Node node) {
        Point point = node.getGeomPx();
        Floor floor = node.getFloor();
        return new RoutingNode(
                node.getId(),
                node.getKindCode(),
                node.getNameKo(),
                floor == null ? null : floor.getId(),
                floor == null ? null : floor.getName(),
                point.getX(),
                point.getY()
        );
    }

    private RoutingEdge toRoutingEdge(Edge edge) {
        return new RoutingEdge(
                edge.getId(),
                edge.getFromNode().getId(),
                edge.getToNode().getId(),
                edge.getKindCode(),
                edge.isDirected(),
                decimalToDouble(edge.getLengthM()),
                decimalToDouble(edge.getBaseWeight())
        );
    }

    private java.util.stream.Stream<VerticalRoutingLink> toVerticalRoutingLinks(List<VerticalConnectorNode> nodes) {
        return nodes.stream()
                .flatMap(from -> nodes.stream()
                        .filter(to -> !from.getNode().getId().equals(to.getNode().getId()))
                        .map(to -> toVerticalRoutingLink(from, to)));
    }

    private VerticalRoutingLink toVerticalRoutingLink(VerticalConnectorNode from, VerticalConnectorNode to) {
        VerticalConnector connector = from.getConnector();
        return new VerticalRoutingLink(
                from.getNode().getId(),
                to.getNode().getId(),
                connector.getKind(),
                connector.getName(),
                from.getFloor().getName(),
                to.getFloor().getName(),
                connector.getAvgWaitSeconds()
        );
    }

    private String resolveMapImageUrl(MapType mapType, UUID ownerId) {
        if (mapType == MapType.CAMPUS) {
            return campusMapRepository.findByCampusIdAndIsCurrentTrue(ownerId)
                    .map(CampusMap::getImageUrl)
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
        return new RoutingObstacle(
                obstacle.getAffectedEdgeIds() == null ? List.of() : obstacle.getAffectedEdgeIds(),
                decimalToDouble(obstacle.getExtraCost()),
                obstacle.isBlocking()
        );
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

        return Optional.of(new IndoorDestinationAnchor(
                campus == null ? null : campus.getId(),
                campus == null ? null : campus.getName(),
                campus == null ? null : campus.getPrimaryEntranceName(),
                campusEntrance == null ? null : campusEntrance.getX(),
                campusEntrance == null ? null : campusEntrance.getY(),
                building.getId(),
                building.getName(),
                node.getId(),
                node.getNameKo(),
                point.getX(),
                point.getY()
        ));
    }

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

    public record RoutingObstacle(
            List<UUID> affectedEdgeIds,
            double extraCost,
            boolean blocking
    ) {
    }

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

    /*
    // 예시: Navigation 팀원이 호출할 메서드 껍데기
    public List<Node> getNodesForRouting(UUID mapVersionId) {
        return nodeRepository.findByMapVersionId(mapVersionId);
    }
    */
}
