package com.insideout.backend.domain.map.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MapEditorVerticalConnectorMapRequestDTO(
        @NotNull UUID floorId,
        @NotNull UUID nodeId
) {}
