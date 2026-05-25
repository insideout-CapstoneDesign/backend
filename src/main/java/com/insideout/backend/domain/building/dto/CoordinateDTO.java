package com.insideout.backend.domain.building.dto;

import jakarta.validation.constraints.NotNull;

public record CoordinateDTO(
        @NotNull double longitude,
        @NotNull double latitude
) {}
