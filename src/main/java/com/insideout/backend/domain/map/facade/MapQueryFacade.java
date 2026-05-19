package com.insideout.backend.domain.map.facade;

import com.insideout.backend.domain.building.entity.Building;
import com.insideout.backend.domain.building.entity.Campus;
import com.insideout.backend.domain.building.entity.BuildingDirectory;
import com.insideout.backend.domain.building.repository.BuildingRepository;
import com.insideout.backend.domain.building.repository.BuildingDirectoryRepository;
import com.insideout.backend.domain.map.entity.Node;
import com.insideout.backend.domain.map.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

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
    // private final EdgeRepository edgeRepository;
    // private final ObstacleRepository obstacleRepository;

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

    /*
    // 예시: Navigation 팀원이 호출할 메서드 껍데기
    public List<Node> getNodesForRouting(UUID mapVersionId) {
        return nodeRepository.findByMapVersionId(mapVersionId);
    }
    */
}
