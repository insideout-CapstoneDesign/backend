package com.insideout.backend.domain.building.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record BuildingCreateRequestDTO(
        @NotBlank String name,
        String address,
        @Min(0) int entranceCount,
        UUID campusId,
        String externalApiId,
        String longitude,
        String latitude,
        List<@NotNull @Valid FloorCreateRequest> floors
) {
    public record FloorCreateRequest(
            int level,
            @NotBlank String name
    ) {}
}
