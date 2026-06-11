package com.insideout.backend.domain.map.service;

import com.insideout.backend.domain.building.entity.Floor;
import com.insideout.backend.domain.building.entity.Floorplan;
import com.insideout.backend.domain.building.repository.FloorRepository;
import com.insideout.backend.domain.building.repository.FloorplanRepository;
import com.insideout.backend.domain.map.dto.response.MapEditorEdgeDTO;
import com.insideout.backend.domain.map.dto.response.MapEditorFloorplanObjectDTO;
import com.insideout.backend.domain.map.dto.response.MapEditorNodeDTO;
import com.insideout.backend.domain.map.dto.response.MapEditorPoiDTO;
import com.insideout.backend.domain.map.dto.response.MapEditorZoneDTO;
import com.insideout.backend.domain.map.dto.response.PublishedMapFloorResponseDTO;
import com.insideout.backend.domain.map.entity.MapVersion;
import com.insideout.backend.domain.map.enums.MapType;
import com.insideout.backend.domain.map.exception.MapErrorCode;
import com.insideout.backend.domain.map.exception.MapException;
import com.insideout.backend.domain.map.repository.EdgeRepository;
import com.insideout.backend.domain.map.repository.FloorplanObjectRepository;
import com.insideout.backend.domain.map.repository.MapVersionRepository;
import com.insideout.backend.domain.map.repository.NodeRepository;
import com.insideout.backend.domain.map.repository.PoiRepository;
import com.insideout.backend.domain.map.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublishedMapService {

    private static final String PUBLISHED_STATUS = "published";

    private final FloorRepository floorRepository;
    private final FloorplanRepository floorplanRepository;
    private final MapVersionRepository mapVersionRepository;
    private final NodeRepository nodeRepository;
    private final EdgeRepository edgeRepository;
    private final PoiRepository poiRepository;
    private final ZoneRepository zoneRepository;
    private final FloorplanObjectRepository floorplanObjectRepository;

    public PublishedMapFloorResponseDTO getPublishedFloorMap(UUID floorId) {
        Floor floor = loadFloor(floorId);
        MapVersion publishedMapVersion = loadPublishedBuildingMapVersion(floor);
        Floorplan floorplan = loadCurrentFloorplan(floorId);
        PublishedFloorGeometry floorGeometry = loadPublishedFloorGeometry(publishedMapVersion, floorId);

        return PublishedMapFloorResponseDTO.of(
                floor,
                floorplan,
                publishedMapVersion,
                floorGeometry.nodes(),
                floorGeometry.edges(),
                floorGeometry.pois(),
                floorGeometry.zones(),
                floorGeometry.floorplanObjects()
        );
    }

    private Floor loadFloor(UUID floorId) {
        return floorRepository.findById(floorId)
                .orElseThrow(() -> new MapException(MapErrorCode.MAP_FLOOR_NOT_FOUND));
    }

    private MapVersion loadPublishedBuildingMapVersion(Floor floor) {
        return mapVersionRepository
                .findFirstByBuildingIdAndMapTypeAndStatusOrderByCreatedAtDesc(
                        floor.getBuilding().getId(),
                        MapType.BUILDING,
                        PUBLISHED_STATUS
                )
                .orElseThrow(() -> new MapException(MapErrorCode.MAP_VERSION_NOT_FOUND));
    }

    private Floorplan loadCurrentFloorplan(UUID floorId) {
        return floorplanRepository.findByFloorIdAndIsCurrentTrue(floorId).orElse(null);
    }

    private PublishedFloorGeometry loadPublishedFloorGeometry(MapVersion mapVersion, UUID floorId) {
        UUID mapVersionId = mapVersion.getId();

        return new PublishedFloorGeometry(
                nodeRepository.findByMapVersionIdAndFloorId(mapVersionId, floorId)
                        .stream()
                        .map(MapEditorNodeDTO::from)
                        .toList(),
                edgeRepository.findByMapVersionIdAndFloorId(mapVersionId, floorId)
                        .stream()
                        .map(MapEditorEdgeDTO::from)
                        .toList(),
                poiRepository.findByMapVersionIdAndFloorId(mapVersionId, floorId)
                        .stream()
                        .map(MapEditorPoiDTO::from)
                        .toList(),
                zoneRepository.findByMapVersionIdAndFloorId(mapVersionId, floorId)
                        .stream()
                        .map(MapEditorZoneDTO::from)
                        .toList(),
                floorplanObjectRepository.findByMapVersionIdAndFloorId(mapVersionId, floorId)
                        .stream()
                        .map(MapEditorFloorplanObjectDTO::from)
                        .toList()
        );
    }

    private record PublishedFloorGeometry(
            List<MapEditorNodeDTO> nodes,
            List<MapEditorEdgeDTO> edges,
            List<MapEditorPoiDTO> pois,
            List<MapEditorZoneDTO> zones,
            List<MapEditorFloorplanObjectDTO> floorplanObjects
    ) {
    }
}
