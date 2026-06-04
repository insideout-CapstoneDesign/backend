package com.insideout.backend.domain.map.dto.request;

import java.util.List;

public record MapEditorDraftSaveRequestDTO(
        List<MapEditorDraftNodeSaveDTO> nodes,
        List<MapEditorDraftEdgeSaveDTO> edges,
        List<MapEditorDraftPoiSaveDTO> pois,
        List<MapEditorDraftZoneSaveDTO> zones
) {
}
