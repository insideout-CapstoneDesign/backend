package com.insideout.backend.domain.building.service;

import com.insideout.backend.domain.building.dto.CoordinateDTO;
import com.insideout.backend.domain.building.dto.CampusGateDTO;
import com.insideout.backend.domain.building.dto.PixelCoordinateDTO;
import com.insideout.backend.domain.building.dto.request.BuildingEntranceCreateRequestDTO;
import com.insideout.backend.domain.building.dto.request.BuildingEntranceMappingCreateRequestDTO;
import com.insideout.backend.domain.building.dto.response.BuildingEntranceResponseDTO;
import com.insideout.backend.domain.building.entity.Building;
import com.insideout.backend.domain.building.entity.BuildingEntranceMapping;
import com.insideout.backend.domain.building.entity.Campus;
import com.insideout.backend.domain.building.entity.Floor;
import com.insideout.backend.domain.building.exception.BuildingErrorCode;
import com.insideout.backend.domain.building.exception.BuildingException;
import com.insideout.backend.domain.building.repository.BuildingEntranceMappingRepository;
import com.insideout.backend.domain.building.repository.BuildingRepository;
import com.insideout.backend.domain.building.repository.FloorRepository;
import com.insideout.backend.domain.map.entity.MapVersion;
import com.insideout.backend.domain.map.entity.Node;
import com.insideout.backend.domain.map.entity.Poi;
import com.insideout.backend.domain.map.entity.PoiCategory;
import com.insideout.backend.domain.map.enums.MapType;
import com.insideout.backend.domain.map.repository.MapVersionRepository;
import com.insideout.backend.domain.map.repository.NodeRepository;
import com.insideout.backend.domain.map.repository.PoiCategoryRepository;
import com.insideout.backend.domain.map.repository.PoiRepository;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BuildingEntranceService {

    private static final GeometryFactory PX_GEOMETRY_FACTORY = new GeometryFactory();
    private static final GeometryFactory WGS84_GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final String ENTRANCE_KIND = "entrance";
    private static final String ENTRANCE_POI_CATEGORY_CODE = "facility.entrance";

    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final MapVersionRepository mapVersionRepository;
    private final NodeRepository nodeRepository;
    private final PoiRepository poiRepository;
    private final PoiCategoryRepository poiCategoryRepository;
    private final BuildingEntranceMappingRepository buildingEntranceMappingRepository;

    @Transactional
    public BuildingEntranceResponseDTO createEntrance(
            UUID tenantId,
            UUID buildingId,
            BuildingEntranceCreateRequestDTO request
    ) {
        Building building = getBuilding(tenantId, buildingId);
        Floor floor = floorRepository.findByIdAndBuilding_Id(request.floorId(), buildingId)
                .orElseThrow(() -> new BuildingException(BuildingErrorCode.FLOOR_NOT_FOUND));

        MapVersion mapVersion = getOrCreateDraftMapVersion(building);
        PoiCategory entranceCategory = poiCategoryRepository.findByCode(ENTRANCE_POI_CATEGORY_CODE)
                .orElseThrow(() -> new BuildingException(BuildingErrorCode.POI_CATEGORY_NOT_FOUND));

        Point geomPx = createPixelPoint(request.pixelCoordinate());
        Point geomWgs84 = createWorldPoint(request.worldCoordinate());
        Map<String, Object> nodeProperties = new HashMap<>();
        if (request.pixelCoordinate() == null) {
            nodeProperties.put("usesSyntheticPx", true);
        }

        Node savedNode = nodeRepository.save(Node.builder()
                .tenantId(tenantId)
                .mapVersion(mapVersion)
                .floor(floor)
                .kindCode(ENTRANCE_KIND)
                .geomPx(geomPx)
                .geomWgs84(geomWgs84)
                .nameKo(request.name())
                .properties(nodeProperties)
                .source("manual")
                .build());

        Poi savedPoi = poiRepository.save(Poi.builder()
                .tenantId(tenantId)
                .mapVersion(mapVersion)
                .floor(floor)
                .categoryId(entranceCategory.getId())
                .name(request.name())
                .geomPx(geomPx)
                .geomWgs84(geomWgs84 != null ? cloneAsPoint2D(geomWgs84) : null)
                .anchorNodeId(savedNode.getId())
                .attrs(Map.of("poiType", "entrance"))
                .source("manual")
                .build());

        syncEntranceCount(building);
        return BuildingEntranceResponseDTO.from(savedNode, savedPoi, null, null);
    }

    public List<BuildingEntranceResponseDTO> getEntrances(UUID tenantId, UUID buildingId) {
        Building building = getBuilding(tenantId, buildingId);

        List<Node> entranceNodes = nodeRepository.findByMapVersion_Building_IdAndKindCodeOrderByCreatedAtAsc(
                buildingId,
                ENTRANCE_KIND
        );
        if (entranceNodes.isEmpty()) {
            return List.of();
        }

        Map<UUID, Poi> poiByAnchorNodeId = poiRepository.findByAnchorNodeIdIn(
                        entranceNodes.stream().map(Node::getId).toList()
                ).stream()
                .collect(Collectors.toMap(Poi::getAnchorNodeId, Function.identity()));

        Map<UUID, BuildingEntranceMapping> mappingByNodeId = buildingEntranceMappingRepository
                .findAllByTenantIdAndBuildingIdOrderByCreatedAtAsc(tenantId, buildingId)
                .stream()
                .collect(Collectors.toMap(BuildingEntranceMapping::getEntranceNodeId, Function.identity()));

        Map<String, String> gateNamesById = extractCampusGateNames(building.getCampus());

        return entranceNodes.stream()
                .map(node -> {
                    BuildingEntranceMapping mapping = mappingByNodeId.get(node.getId());
                    String gateId = mapping != null ? mapping.getCampusGateId() : null;
                    return BuildingEntranceResponseDTO.from(
                            node,
                            poiByAnchorNodeId.get(node.getId()),
                            gateId,
                            gateId != null ? gateNamesById.get(gateId) : null
                    );
                })
                .toList();
    }

    @Transactional
    public BuildingEntranceResponseDTO mapCampusGate(
            UUID tenantId,
            UUID buildingId,
            BuildingEntranceMappingCreateRequestDTO request
    ) {
        Building building = getBuilding(tenantId, buildingId);
        Campus campus = building.getCampus();
        if (campus == null) {
            throw new BuildingException(BuildingErrorCode.ENTRANCE_MAPPING_REQUIRES_CAMPUS);
        }

        Map<String, String> gateNamesById = extractCampusGateNames(campus);
        String campusGateName = gateNamesById.get(request.campusGateId());
        if (campusGateName == null) {
            throw new BuildingException(BuildingErrorCode.INVALID_CAMPUS_GATE);
        }

        Node entranceNode = nodeRepository.findById(request.entranceNodeId())
                .orElseThrow(() -> new BuildingException(BuildingErrorCode.BUILDING_ENTRANCE_NOT_FOUND));

        boolean belongsToBuilding = entranceNode.getMapVersion() != null
                && entranceNode.getMapVersion().getBuilding() != null
                && buildingId.equals(entranceNode.getMapVersion().getBuilding().getId());
        if (!belongsToBuilding) {
            throw new BuildingException(BuildingErrorCode.BUILDING_ENTRANCE_NOT_FOUND);
        }

        Optional<BuildingEntranceMapping> existingByGate = buildingEntranceMappingRepository
                .findByTenantIdAndCampusIdAndCampusGateId(tenantId, campus.getId(), request.campusGateId());
        existingByGate.ifPresent(buildingEntranceMappingRepository::delete);

        Optional<BuildingEntranceMapping> existingByNode = buildingEntranceMappingRepository
                .findByTenantIdAndBuildingIdAndEntranceNodeId(tenantId, buildingId, request.entranceNodeId());
        existingByNode.ifPresent(buildingEntranceMappingRepository::delete);

        Poi entrancePoi = poiRepository.findByAnchorNodeIdIn(List.of(entranceNode.getId()))
                .stream()
                .findFirst()
                .orElse(null);

        // 캠퍼스 게이트와 매핑 시, 해당 노드의 유형(kind)도 "entrance"로 데이터베이스에 자동 갱신되도록 처리합니다.
        if (!"entrance".equals(entranceNode.getKindCode())) {
            entranceNode.updateKindCode("entrance");
        }

        buildingEntranceMappingRepository.save(BuildingEntranceMapping.builder()
                .tenantId(tenantId)
                .campusId(campus.getId())
                .buildingId(buildingId)
                .campusGateId(request.campusGateId())
                .entranceNodeId(entranceNode.getId())
                .entrancePoiId(entrancePoi != null ? entrancePoi.getId() : null)
                .build());

        return BuildingEntranceResponseDTO.from(
                entranceNode,
                entrancePoi,
                request.campusGateId(),
                campusGateName
        );
    }

    private Building getBuilding(UUID tenantId, UUID buildingId) {
        return buildingRepository.findByIdAndTenant_Id(buildingId, tenantId)
                .orElseThrow(() -> new BuildingException(BuildingErrorCode.BUILDING_NOT_FOUND));
    }

    private MapVersion getOrCreateDraftMapVersion(Building building) {
        return mapVersionRepository
                .findFirstByBuildingIdAndMapTypeAndStatusOrderByCreatedAtDesc(building.getId(), MapType.BUILDING, "draft")
                .orElseGet(() -> {
                    UUID parentVersionId = mapVersionRepository
                            .findFirstByBuildingIdAndMapTypeAndStatusOrderByCreatedAtDesc(
                                    building.getId(),
                                    MapType.BUILDING,
                                    "published"
                            )
                            .map(MapVersion::getId)
                            .orElse(null);

                    return mapVersionRepository.save(MapVersion.builder()
                            .tenantId(building.getTenant().getId())
                            .building(building)
                            .mapType(MapType.BUILDING)
                            .label(building.getName() + " 출입구 설정 초안")
                            .status("draft")
                            .parentVersionId(parentVersionId)
                            .build());
                });
    }

    private void syncEntranceCount(Building building) {
        long count = nodeRepository.countByMapVersion_Building_IdAndKindCode(building.getId(), ENTRANCE_KIND);
        building.updateEntranceCount((int) count);
    }

    private Point createPixelPoint(PixelCoordinateDTO pixelCoordinate) {
        if (pixelCoordinate == null) {
            Point fallback = PX_GEOMETRY_FACTORY.createPoint(new Coordinate(0, 0));
            fallback.setSRID(0);
            return fallback;
        }
        Point point = PX_GEOMETRY_FACTORY.createPoint(new Coordinate(pixelCoordinate.x(), pixelCoordinate.y()));
        point.setSRID(0);
        return point;
    }

    private Point createWorldPoint(CoordinateDTO worldCoordinate) {
        if (worldCoordinate == null) {
            return null;
        }
        Point point = WGS84_GEOMETRY_FACTORY.createPoint(new Coordinate(worldCoordinate.longitude(), worldCoordinate.latitude()));
        point.setSRID(4326);
        return point;
    }

    private Point cloneAsPoint2D(Point point) {
        Point cloned = WGS84_GEOMETRY_FACTORY.createPoint(new Coordinate(point.getX(), point.getY()));
        cloned.setSRID(4326);
        return cloned;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> extractCampusGateNames(Campus campus) {
        if (campus == null || campus.getMeta() == null) {
            return Map.of();
        }

        Object gatesValue = campus.getMeta().get("gates");
        if (!(gatesValue instanceof List<?> gates)) {
            return Map.of();
        }

        return gates.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(entry -> {
                    Object id = entry.get("id");
                    Object name = entry.get("name");
                    if (id == null || name == null) {
                        return null;
                    }
                    return new CampusGateDTO(String.valueOf(id), String.valueOf(name), null);
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toMap(CampusGateDTO::id, CampusGateDTO::name, (left, right) -> left));
    }
}
