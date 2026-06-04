package com.insideout.backend.domain.map.dto.request;

import java.util.UUID;

public record MapEditorVerticalConnectorMapRequestDTO(
        UUID floorId,
        UUID nodeId
) {}
