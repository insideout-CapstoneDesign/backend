package com.insideout.backend.domain.map.dto.request;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record MapEditorDraftEdgeSaveDTO(
        UUID id,
        UUID fromNodeId,
        UUID toNodeId,
        String kind,
        Map<String, Object> geomPx,
        Boolean isDirected,
        BigDecimal baseWeight,
        Map<String, Object> properties
) {
}
