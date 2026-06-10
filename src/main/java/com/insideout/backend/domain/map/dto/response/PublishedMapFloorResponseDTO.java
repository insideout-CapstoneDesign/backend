package com.insideout.backend.domain.map.dto.response;

import com.insideout.backend.domain.building.entity.Floor;
import com.insideout.backend.domain.building.entity.Floorplan;
import com.insideout.backend.domain.map.entity.MapVersion;

import java.util.List;
import java.util.UUID;

public record PublishedMapFloorResponseDTO(
        UUID buildingId,
        String buildingName,
        UUID floorId,
        String floorName,
        UUID floorplanId,
        String floorplanImageUrl,
        Integer floorplanWidthPx,
        Integer floorplanHeightPx,
        UUID mapVersionId,
        List<MapEditorNodeDTO> nodes,
        List<MapEditorEdgeDTO> edges,
        List<MapEditorPoiDTO> pois,
        List<MapEditorZoneDTO> zones,
        List<MapEditorFloorplanObjectDTO> floorplanObjects
) {
    public static PublishedMapFloorResponseDTO of(
            Floor floor,
            Floorplan floorplan,
            MapVersion mapVersion,
            List<MapEditorNodeDTO> nodes,
            List<MapEditorEdgeDTO> edges,
            List<MapEditorPoiDTO> pois,
            List<MapEditorZoneDTO> zones,
            List<MapEditorFloorplanObjectDTO> floorplanObjects
    ) {
        return new PublishedMapFloorResponseDTO(
                floor.getBuilding().getId(),
                floor.getBuilding().getName(),
                floor.getId(),
                floor.getName(),
                floorplan != null ? floorplan.getId() : null,
                floorplan != null ? floorplan.getImageUrl() : null,
                floorplan != null ? floorplan.getWidthPx() : null,
                floorplan != null ? floorplan.getHeightPx() : null,
                mapVersion.getId(),
                nodes,
                edges,
                pois,
                zones,
                floorplanObjects
        );
    }
}
