package com.insideout.backend.domain.map.dto.response;

import com.insideout.backend.domain.building.entity.Floor;
import com.insideout.backend.domain.building.entity.Floorplan;

import java.util.UUID;

public record MapEditorInitBuildingDraftFloorDTO(
        UUID floorId,
        String floorName,
        int level,
        UUID floorplanId,
        boolean analyzed,
        boolean draftReady,
        boolean initializedFromAi
) {
    public static MapEditorInitBuildingDraftFloorDTO of(
            Floor floor,
            Floorplan floorplan,
            boolean analyzed,
            boolean draftReady,
            boolean initializedFromAi
    ) {
        return new MapEditorInitBuildingDraftFloorDTO(
                floor.getId(),
                floor.getName(),
                floor.getLevel(),
                floorplan != null ? floorplan.getId() : null,
                analyzed,
                draftReady,
                initializedFromAi
        );
    }
}
