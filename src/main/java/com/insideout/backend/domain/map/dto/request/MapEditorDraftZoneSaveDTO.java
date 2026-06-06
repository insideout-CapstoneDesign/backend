package com.insideout.backend.domain.map.dto.request;

import java.util.Map;
import java.util.UUID;

public record MapEditorDraftZoneSaveDTO(
        UUID id,
        String kind,
        String name,
        Map<String, Object> geomPx,
        Map<String, Object> properties
) {
}
