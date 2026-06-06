package com.insideout.backend.domain.building.dto.request;

import com.insideout.backend.domain.building.dto.CoordinateDTO;
import com.insideout.backend.domain.building.dto.PixelCoordinateDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record BuildingEntranceCreateRequestDTO(
        @NotNull UUID floorId,
        @NotBlank String name,
        @Valid PixelCoordinateDTO pixelCoordinate,
        @Valid CoordinateDTO worldCoordinate
) {
}
