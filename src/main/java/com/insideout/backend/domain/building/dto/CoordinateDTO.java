package com.insideout.backend.domain.building.dto;

import jakarta.validation.constraints.NotNull;

public record CoordinateDTO(
        @NotNull Double longitude,
        @NotNull Double latitude
) {}
