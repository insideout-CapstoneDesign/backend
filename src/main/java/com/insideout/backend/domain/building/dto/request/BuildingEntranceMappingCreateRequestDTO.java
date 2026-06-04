package com.insideout.backend.domain.building.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record BuildingEntranceMappingCreateRequestDTO(
        @NotBlank String campusGateId,
        @NotNull UUID entranceNodeId
) {
}
