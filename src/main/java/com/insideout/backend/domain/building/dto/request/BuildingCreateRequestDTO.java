package com.insideout.backend.domain.building.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record BuildingCreateRequestDTO(
        @NotBlank String name,
        String address,
        @Min(0) int entranceCount,
        UUID campusId,
        String externalApiId
) {
}
