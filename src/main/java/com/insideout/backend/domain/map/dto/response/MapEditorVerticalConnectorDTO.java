package com.insideout.backend.domain.map.dto.response;

import java.util.List;
import java.util.UUID;

public record MapEditorVerticalConnectorDTO(
        UUID id,
        String kind,
        String name,
        List<MapEditorVerticalConnectorNodeDTO> nodes
) {}
