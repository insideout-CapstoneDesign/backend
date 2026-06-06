package com.insideout.backend.domain.map.dto.request;

import java.util.Map;
import java.util.UUID;

public record MapEditorDraftPoiSaveDTO(
        UUID id,
        String name,
        String code,
        Map<String, Object> geomPx,
        Map<String, Object> footprintPx,
        UUID anchorNodeId,
        Map<String, Object> attrs,
        String externalApiId
) {
}
