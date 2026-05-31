package com.insideout.backend.domain.building.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CampusGateDTO(
        String id,
        @NotBlank String name,
        @NotNull @Valid CoordinateDTO location
) {}
