package com.insideout.backend.domain.map.service;

import com.insideout.backend.domain.building.entity.Building;
import com.insideout.backend.domain.building.entity.BuildingEntranceMapping;
import com.insideout.backend.domain.building.entity.Floor;
import com.insideout.backend.domain.building.repository.BuildingEntranceMappingRepository;
import com.insideout.backend.domain.building.repository.FloorRepository;
import com.insideout.backend.domain.map.entity.Edge;
import com.insideout.backend.domain.map.entity.FloorplanObject;
import com.insideout.backend.domain.map.entity.MapVersion;
import com.insideout.backend.domain.map.entity.Node;
import com.insideout.backend.domain.map.entity.Poi;
import com.insideout.backend.domain.map.entity.VerticalConnector;
import com.insideout.backend.domain.map.entity.VerticalConnectorNode;
import com.insideout.backend.domain.map.entity.Zone;
import com.insideout.backend.domain.map.enums.MapType;
import com.insideout.backend.domain.map.repository.EdgeRepository;
import com.insideout.backend.domain.map.repository.FloorplanObjectRepository;
import com.insideout.backend.domain.map.repository.MapVersionRepository;
import com.insideout.backend.domain.map.repository.NodeRepository;
import com.insideout.backend.domain.map.repository.PoiRepository;
import com.insideout.backend.domain.map.repository.VerticalConnectorNodeRepository;
import com.insideout.backend.domain.map.repository.VerticalConnectorRepository;
import com.insideout.backend.domain.map.repository.ZoneRepository;
import com.insideout.backend.domain.user.entity.User;
import com.insideout.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MapEditorDraftVersionService {

    private final MapVersionRepository mapVersionRepository;
    private final UserRepository userRepository;
    private final FloorRepository floorRepository;
    private final NodeRepository nodeRepository;
    private final ZoneRepository zoneRepository;
    private final FloorplanObjectRepository floorplanObjectRepository;
    private final PoiRepository poiRepository;
    private final EdgeRepository edgeRepository;
    private final VerticalConnectorRepository verticalConnectorRepository;
    private final VerticalConnectorNodeRepository verticalConnectorNodeRepository;
    private final BuildingEntranceMappingRepository buildingEntranceMappingRepository;

    public DraftMapVersionResult getOrCreateBuildingDraftMapVersion(Building building, UUID userId) {
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

    public record DraftMapVersionResult(MapVersion mapVersion, boolean created) {
    }
}
