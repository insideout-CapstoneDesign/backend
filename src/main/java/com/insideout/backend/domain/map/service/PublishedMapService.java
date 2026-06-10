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

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublishedMapService {

    private final FloorRepository floorRepository;
    private final FloorplanRepository floorplanRepository;
    private final MapVersionRepository mapVersionRepository;
    private final NodeRepository nodeRepository;
    private final EdgeRepository edgeRepository;
    private final PoiRepository poiRepository;
    private final ZoneRepository zoneRepository;
    private final FloorplanObjectRepository floorplanObjectRepository;

    public PublishedMapFloorResponseDTO getPublishedFloorMap(UUID floorId) {
        Floor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> new MapException(MapErrorCode.MAP_FLOOR_NOT_FOUND));

        MapVersion publishedMapVersion = mapVersionRepository
                .findFirstByBuildingIdAndMapTypeAndStatusOrderByCreatedAtDesc(
                        floor.getBuilding().getId(),
                        MapType.BUILDING,
                        "published"
                )
                .orElseThrow(() -> new MapException(MapErrorCode.MAP_VERSION_NOT_FOUND));

        Floorplan floorplan = floorplanRepository.findByFloorIdAndIsCurrentTrue(floorId).orElse(null);

        return PublishedMapFloorResponseDTO.of(
                floor,
                floorplan,
                publishedMapVersion,
                nodeRepository.findByMapVersionIdAndFloorId(publishedMapVersion.getId(), floorId)
                        .stream()
                        .map(MapEditorNodeDTO::from)
                        .toList(),
                edgeRepository.findByMapVersionIdAndFloorId(publishedMapVersion.getId(), floorId)
                        .stream()
                        .map(MapEditorEdgeDTO::from)
                        .toList(),
                poiRepository.findByMapVersionIdAndFloorId(publishedMapVersion.getId(), floorId)
                        .stream()
                        .map(MapEditorPoiDTO::from)
                        .toList(),
                zoneRepository.findByMapVersionIdAndFloorId(publishedMapVersion.getId(), floorId)
                        .stream()
                        .map(MapEditorZoneDTO::from)
                        .toList(),
                floorplanObjectRepository.findByMapVersionIdAndFloorId(publishedMapVersion.getId(), floorId)
                        .stream()
                        .map(MapEditorFloorplanObjectDTO::from)
                        .toList()
        );
    }
}
