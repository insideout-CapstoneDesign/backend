package com.insideout.backend.domain.map.service;

import com.insideout.backend.domain.building.entity.Building;
import com.insideout.backend.domain.building.entity.Floor;
import com.insideout.backend.domain.building.exception.BuildingErrorCode;
import com.insideout.backend.domain.building.exception.BuildingException;
import com.insideout.backend.domain.building.repository.BuildingRepository;
import com.insideout.backend.domain.building.repository.FloorRepository;
import com.insideout.backend.domain.map.dto.request.MapEditorVerticalConnectorCreateRequestDTO;
import com.insideout.backend.domain.map.dto.request.MapEditorVerticalConnectorMapRequestDTO;
import com.insideout.backend.domain.map.dto.response.MapEditorVerticalConnectorDTO;
import com.insideout.backend.domain.map.dto.response.MapEditorVerticalConnectorNodeDTO;
import com.insideout.backend.domain.map.entity.MapVersion;
import com.insideout.backend.domain.map.entity.Node;
import com.insideout.backend.domain.map.entity.VerticalConnector;
import com.insideout.backend.domain.map.entity.VerticalConnectorNode;
import com.insideout.backend.domain.map.enums.MapType;
import com.insideout.backend.domain.map.exception.MapErrorCode;
import com.insideout.backend.domain.map.exception.MapException;
import com.insideout.backend.domain.map.repository.MapVersionRepository;
import com.insideout.backend.domain.map.repository.NodeRepository;
import com.insideout.backend.domain.map.repository.VerticalConnectorNodeRepository;
import com.insideout.backend.domain.map.repository.VerticalConnectorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MapEditorVerticalConnectorService {

    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final MapVersionRepository mapVersionRepository;
    private final NodeRepository nodeRepository;
    private final VerticalConnectorRepository verticalConnectorRepository;
    private final VerticalConnectorNodeRepository verticalConnectorNodeRepository;
    private final MapEditorDraftVersionService mapEditorDraftVersionService;

    @Transactional(readOnly = true)
    public List<MapEditorVerticalConnectorDTO> getVerticalConnectors(UUID tenantId, UUID buildingId) {
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
        for (VerticalConnectorNode connectorNode : connectorNodes) {
            UUID connectorId = connectorNode.getConnector().getId();
            MapEditorVerticalConnectorNodeDTO nodeDTO = new MapEditorVerticalConnectorNodeDTO(
                    connectorNode.getFloor().getId(),
                    connectorNode.getFloor().getName(),
                    connectorNode.getNode().getId(),
                    connectorNode.getNode().getNameKo() != null
                            ? connectorNode.getNode().getNameKo()
                            : (connectorNode.getNode().getKindCode() + " 노드")
            );
            nodesByConnectorId.computeIfAbsent(connectorId, key -> new ArrayList<>()).add(nodeDTO);
        }

        List<MapEditorVerticalConnectorDTO> result = new ArrayList<>();
        for (VerticalConnector connector : connectors) {
            result.add(new MapEditorVerticalConnectorDTO(
                    connector.getId(),
                    connector.getKind(),
                    connector.getName(),
                    connector.getAvgWaitSeconds(),
                    connector.getDirection(),
                    nodesByConnectorId.getOrDefault(connector.getId(), List.of())
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

        MapEditorDraftVersionService.DraftMapVersionResult draftResult =
                mapEditorDraftVersionService.getOrCreateBuildingDraftMapVersion(building, userId);
        MapVersion draftMapVersion = draftResult.mapVersion();

        // 종류별 평균 대기 시간 기본값 정의
        Integer resolvedWaitSeconds = resolveAvgWaitSeconds(request.avgWaitSeconds(), request.kind());

        VerticalConnector connector = VerticalConnector.builder()
                .tenantId(tenantId)
                .mapVersion(draftMapVersion)
                .building(building)
                .kind(request.kind())
                .name(request.name())
                .avgWaitSeconds(resolvedWaitSeconds)
                .direction(request.direction())
                .build();

        VerticalConnector saved = verticalConnectorRepository.save(connector);
        return new MapEditorVerticalConnectorDTO(saved.getId(), saved.getKind(), saved.getName(), saved.getAvgWaitSeconds(), saved.getDirection(), List.of());
    }

    @Transactional
    public void deleteVerticalConnector(UUID tenantId, UUID buildingId, UUID connectorId) {
        VerticalConnector connector = verticalConnectorRepository.findById(connectorId)
                .orElseThrow(() -> new BuildingException(BuildingErrorCode.VERTICAL_CONNECTOR_NOT_FOUND));

        validateConnectorOwnership(connector, tenantId, buildingId);

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
            UUID floorId,
            MapEditorVerticalConnectorMapRequestDTO request
    ) {
        VerticalConnector connector = verticalConnectorRepository.findById(connectorId)
                .orElseThrow(() -> new BuildingException(BuildingErrorCode.VERTICAL_CONNECTOR_NOT_FOUND));

        validateConnectorOwnership(connector, tenantId, buildingId);

        MapVersion draftMapVersion = getDraftMapVersionOrThrow(buildingId);
        if (!connector.getMapVersion().getId().equals(draftMapVersion.getId())) {
            throw new MapException(MapErrorCode.MAP_VERSION_NOT_EDITABLE);
        }

        Node node = nodeRepository.findById(request.nodeId())
                .orElseThrow(() -> new BuildingException(BuildingErrorCode.BUILDING_NOT_FOUND));

        Floor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> new BuildingException(BuildingErrorCode.FLOOR_NOT_FOUND));

        if (!tenantId.equals(node.getTenantId())) {
            throw new MapException(MapErrorCode.VERTICAL_CONNECTOR_TENANT_MISMATCH);
        }

        if (node.getFloor() == null || !floorId.equals(node.getFloor().getId())) {
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

        verticalConnectorNodeRepository.deleteByConnectorIdAndFloorId(connectorId, floorId);
        verticalConnectorNodeRepository.flush();

        VerticalConnectorNode mapping = VerticalConnectorNode.builder()
                .tenantId(tenantId)
                .connector(connector)
                .node(node)
                .floor(floor)
                .build();

        verticalConnectorNodeRepository.save(mapping);
        verticalConnectorNodeRepository.flush();

        return getVerticalConnectors(tenantId, buildingId).stream()
                .filter(dto -> dto.id().equals(connector.getId()))
                .findFirst()
                .orElse(new MapEditorVerticalConnectorDTO(
                        connector.getId(),
                        connector.getKind(),
                        connector.getName(),
                        connector.getAvgWaitSeconds(),
                        connector.getDirection(),
                        List.of()
                ));
    }

    @Transactional
    public MapEditorVerticalConnectorDTO unmapVerticalConnectorNode(
            UUID tenantId,
            UUID buildingId,
            UUID connectorId,
            UUID floorId
    ) {
        VerticalConnector connector = verticalConnectorRepository.findById(connectorId)
                .orElseThrow(() -> new BuildingException(BuildingErrorCode.VERTICAL_CONNECTOR_NOT_FOUND));

        validateConnectorOwnership(connector, tenantId, buildingId);

        MapVersion draftMapVersion = getDraftMapVersionOrThrow(buildingId);
        if (!connector.getMapVersion().getId().equals(draftMapVersion.getId())) {
            throw new MapException(MapErrorCode.MAP_VERSION_NOT_EDITABLE);
        }

        verticalConnectorNodeRepository.deleteByConnectorIdAndFloorId(connectorId, floorId);
        verticalConnectorNodeRepository.flush();

        return getVerticalConnectors(tenantId, buildingId).stream()
                .filter(dto -> dto.id().equals(connector.getId()))
                .findFirst()
                .orElse(new MapEditorVerticalConnectorDTO(
                        connector.getId(),
                        connector.getKind(),
                        connector.getName(),
                        connector.getAvgWaitSeconds(),
                        connector.getDirection(),
                        List.of()
                ));
    }

    private MapVersion getDraftMapVersionOrThrow(UUID buildingId) {
        return mapVersionRepository
                .findFirstByBuildingIdAndMapTypeAndStatusOrderByCreatedAtDesc(buildingId, MapType.BUILDING, "draft")
                .orElseThrow(() -> new MapException(MapErrorCode.MAP_VERSION_NOT_FOUND));
    }

    private void validateConnectorOwnership(VerticalConnector connector, UUID tenantId, UUID buildingId) {
        if (!connector.getTenantId().equals(tenantId) || !connector.getBuilding().getId().equals(buildingId)) {
            throw new BuildingException(BuildingErrorCode.VERTICAL_CONNECTOR_NOT_FOUND);
        }
    }

    @Transactional
    public MapEditorVerticalConnectorDTO updateVerticalConnector(
            UUID tenantId,
            UUID buildingId,
            UUID connectorId,
            com.insideout.backend.domain.map.dto.request.MapEditorVerticalConnectorUpdateRequestDTO request
    ) {
        VerticalConnector connector = verticalConnectorRepository.findById(connectorId)
                .orElseThrow(() -> new BuildingException(BuildingErrorCode.VERTICAL_CONNECTOR_NOT_FOUND));

        validateConnectorOwnership(connector, tenantId, buildingId);

        MapVersion draftMapVersion = getDraftMapVersionOrThrow(buildingId);
        if (!connector.getMapVersion().getId().equals(draftMapVersion.getId())) {
            throw new MapException(MapErrorCode.MAP_VERSION_NOT_EDITABLE);
        }

        Integer resolvedWaitSeconds = resolveAvgWaitSeconds(
                request.avgWaitSeconds(),
                request.kind() != null ? request.kind() : connector.getKind()
        );

        connector.update(request.name(), request.kind(), resolvedWaitSeconds, request.direction());
        verticalConnectorRepository.save(connector);

        return getVerticalConnectors(tenantId, buildingId).stream()
                .filter(dto -> dto.id().equals(connector.getId()))
                .findFirst()
                .orElse(new MapEditorVerticalConnectorDTO(
                        connector.getId(),
                        connector.getKind(),
                        connector.getName(),
                        connector.getAvgWaitSeconds(),
                        connector.getDirection(),
                        List.of()
                ));
    }

    /**
     * avgWaitSeconds가 null일 때 kind에 따라 기본값을 반환합니다.
     * create / update 모두 동일한 정책을 공유합니다.
     */
    private Integer resolveAvgWaitSeconds(Integer avgWaitSeconds, String kind) {
        if (avgWaitSeconds != null) {
            return avgWaitSeconds;
        }
        return switch (kind != null ? kind.toLowerCase() : "") {
            case "elevator" -> 30;
            case "stair" -> 20;
            case "escalator" -> 15;
            case "ramp" -> 10;
            default -> 15;
        };
    }
}
