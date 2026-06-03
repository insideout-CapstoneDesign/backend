package com.insideout.backend.domain.map.dto.response;

import com.insideout.backend.domain.ai.dto.response.DetectionViewDTO;
import com.insideout.backend.domain.building.entity.Building;
import com.insideout.backend.domain.building.entity.Floor;
import com.insideout.backend.domain.building.entity.Floorplan;
import com.insideout.backend.domain.map.entity.MapVersion;

import java.util.List;
import java.util.UUID;

public record MapEditorInitResponseDTO(
        UUID buildingId,
        String buildingName,
        UUID floorId,
        String floorName,
        UUID floorplanId,
        String floorplanImageUrl,
        Integer floorplanWidthPx,
        Integer floorplanHeightPx,
        UUID mapVersionId,
        String mapVersionStatus,
        boolean draftCreated,
        boolean initializedFromAi,
        List<DetectionViewDTO> aiDetections,
        List<MapEditorNodeDTO> nodes,
        List<MapEditorEdgeDTO> edges,
        List<MapEditorPoiDTO> pois,
        List<MapEditorZoneDTO> zones,
        List<MapEditorFloorplanObjectDTO> floorplanObjects
) {
    public static MapEditorInitResponseDTO of(
            Building building,
            Floor floor,
            Floorplan floorplan,
            String floorplanImageUrl,
            MapVersion mapVersion,
            boolean draftCreated,
            boolean initializedFromAi,
            List<DetectionViewDTO> aiDetections,
            List<MapEditorNodeDTO> nodes,
            List<MapEditorEdgeDTO> edges,
            List<MapEditorPoiDTO> pois,
            List<MapEditorZoneDTO> zones,
            List<MapEditorFloorplanObjectDTO> floorplanObjects
    ) {
        return new MapEditorInitResponseDTO(
                building.getId(),
                building.getName(),
                floor.getId(),
                floor.getName(),
                floorplan != null ? floorplan.getId() : null,
                floorplan != null? floorplanImageUrl : null,
                floorplan != null ? floorplan.getWidthPx() : null,
                floorplan != null ? floorplan.getHeightPx() : null,
                mapVersion.getId(),
                mapVersion.getStatus(),
                draftCreated,
                initializedFromAi,
                aiDetections,
                nodes,
                edges,
                pois,
                zones,
                floorplanObjects
        );
    }
}
