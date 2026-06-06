package com.insideout.backend.domain.map.dto.response;

import java.util.UUID;

public record MapEditorVerticalConnectorNodeDTO(
        UUID floorId,
        String floorName,
        UUID nodeId,
        String nodeName
) {}
