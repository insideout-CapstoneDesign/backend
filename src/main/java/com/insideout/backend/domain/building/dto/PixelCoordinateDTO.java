package com.insideout.backend.domain.building.dto;

import jakarta.validation.constraints.NotNull;

public record PixelCoordinateDTO(
        @NotNull Double x,
        @NotNull Double y
) {
}
