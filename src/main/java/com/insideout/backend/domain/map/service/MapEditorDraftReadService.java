package com.insideout.backend.domain.map.service;

import com.insideout.backend.domain.ai.dto.response.DetectionViewDTO;
import com.insideout.backend.domain.ai.entity.AiDetection;
import com.insideout.backend.domain.ai.repository.AiDetectionRepository;
import com.insideout.backend.domain.building.entity.Building;
import com.insideout.backend.domain.building.entity.Floor;
import com.insideout.backend.domain.building.entity.Floorplan;
import com.insideout.backend.domain.building.repository.FloorplanRepository;
import com.insideout.backend.domain.map.dto.response.MapEditorEdgeDTO;
import com.insideout.backend.domain.map.dto.response.MapEditorFloorplanObjectDTO;
import com.insideout.backend.domain.map.dto.response.MapEditorInitBuildingDraftFloorDTO;
import com.insideout.backend.domain.map.dto.response.MapEditorInitBuildingDraftResponseDTO;
import com.insideout.backend.domain.map.dto.response.MapEditorInitResponseDTO;
import com.insideout.backend.domain.map.dto.response.MapEditorNodeDTO;
import com.insideout.backend.domain.map.dto.response.MapEditorPoiDTO;
import com.insideout.backend.domain.map.dto.response.MapEditorZoneDTO;
import com.insideout.backend.domain.map.entity.MapVersion;
import com.insideout.backend.domain.map.repository.EdgeRepository;
import com.insideout.backend.domain.map.repository.FloorplanObjectRepository;
import com.insideout.backend.domain.map.repository.NodeRepository;
import com.insideout.backend.domain.map.repository.PoiRepository;
import com.insideout.backend.domain.map.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MapEditorDraftReadService {

    private final FloorplanRepository floorplanRepository;
    private final AiDetectionRepository aiDetectionRepository;
    private final NodeRepository nodeRepository;
    private final EdgeRepository edgeRepository;
    private final PoiRepository poiRepository;
    private final ZoneRepository zoneRepository;
    private final FloorplanObjectRepository floorplanObjectRepository;

    public MapEditorInitResponseDTO buildFloorDraftResponse(
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
                aiDetections.stream()
                        .map(DetectionViewDTO::from)
                        .toList(),
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

    public MapEditorInitBuildingDraftResponseDTO buildBuildingDraftResponse(
            UUID tenantId,
            Building building,
            List<Floor> floors,
            MapVersion draftMapVersion,
            boolean draftCreated
    ) {
        List<UUID> floorIds = floors.stream().map(Floor::getId).toList();
        Map<UUID, Floorplan> currentFloorplansByFloorId = floorIds.isEmpty()
                ? Map.of()
                : floorplanRepository.findAllByFloorIdInAndIsCurrentTrue(floorIds).stream()
                .collect(Collectors.toMap(
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
                : Set.copyOf(aiDetectionRepository.findAnalyzedFloorplanIdsByTenantIdAndFloorplanIds(tenantId, currentFloorplanIds));

        List<MapEditorInitBuildingDraftFloorDTO> floorStates = floors.stream()
                .map(floor -> {
                    Floorplan currentFloorplan = currentFloorplansByFloorId.get(floor.getId());
                    boolean analyzed = currentFloorplan != null && analyzedFloorplanIds.contains(currentFloorplan.getId());
                    FloorDraftContentState contentState = getFloorDraftContentState(draftMapVersion.getId(), floor.getId());
                    return MapEditorInitBuildingDraftFloorDTO.of(
                            floor,
                            currentFloorplan,
                            analyzed,
                            contentState.isFullyReady(),
                            false
                    );
                })
                .toList();

        int analyzedFloorCount = (int) floorStates.stream().filter(MapEditorInitBuildingDraftFloorDTO::analyzed).count();
        int draftReadyFloorCount = (int) floorStates.stream().filter(MapEditorInitBuildingDraftFloorDTO::draftReady).count();

        return MapEditorInitBuildingDraftResponseDTO.of(
                building,
                draftMapVersion,
                draftCreated,
                analyzedFloorCount,
                draftReadyFloorCount,
                floorStates
        );
    }

    public FloorDraftContentState getFloorDraftContentState(UUID mapVersionId, UUID floorId) {
        return new FloorDraftContentState(
                !zoneRepository.findByMapVersionIdAndFloorId(mapVersionId, floorId).isEmpty(),
                !floorplanObjectRepository.findByMapVersionIdAndFloorId(mapVersionId, floorId).isEmpty(),
                !edgeRepository.findByMapVersionIdAndFloorId(mapVersionId, floorId).isEmpty(),
                !nodeRepository.findByMapVersionIdAndFloorId(mapVersionId, floorId).isEmpty(),
                !poiRepository.findByMapVersionIdAndFloorId(mapVersionId, floorId).isEmpty()
        );
    }

    public record FloorDraftContentState(
            boolean hasZones,
            boolean hasFloorplanObjects,
            boolean hasEdges,
            boolean hasNodes,
            boolean hasPois
    ) {
        public boolean hasAnyContent() {
            return hasZones || hasFloorplanObjects || hasEdges || hasNodes || hasPois;
        }

        public boolean hasMissingContent() {
            return !hasZones || !hasFloorplanObjects || !hasEdges || !hasNodes || !hasPois;
        }

        public boolean isFullyReady() {
            return hasZones && hasFloorplanObjects && hasEdges && hasNodes && hasPois;
        }
    }
}
