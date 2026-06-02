package com.insideout.backend.domain.map.dto.response;

import com.insideout.backend.domain.building.entity.Building;
import com.insideout.backend.domain.map.entity.MapVersion;

import java.util.List;
import java.util.UUID;

public record MapEditorInitBuildingDraftResponseDTO(
        UUID buildingId,
        String buildingName,
        UUID mapVersionId,
        String mapVersionStatus,
        boolean draftCreated,
        int analyzedFloorCount,
        int draftReadyFloorCount,
        List<MapEditorInitBuildingDraftFloorDTO> floors
) {
    public static MapEditorInitBuildingDraftResponseDTO of(
            Building building,
            MapVersion mapVersion,
            boolean draftCreated,
            int analyzedFloorCount,
            int draftReadyFloorCount,
            List<MapEditorInitBuildingDraftFloorDTO> floors
    ) {
        return new MapEditorInitBuildingDraftResponseDTO(
                building.getId(),
                building.getName(),
                mapVersion.getId(),
                mapVersion.getStatus(),
                draftCreated,
                analyzedFloorCount,
                draftReadyFloorCount,
                floors
        );
    }
}
