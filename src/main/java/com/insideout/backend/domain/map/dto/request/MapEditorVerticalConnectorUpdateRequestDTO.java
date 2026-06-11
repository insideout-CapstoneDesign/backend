package com.insideout.backend.domain.map.dto.request;

public record MapEditorVerticalConnectorUpdateRequestDTO(
        String kind,
        String name,
        Integer avgWaitSeconds,
        String direction
) {}
