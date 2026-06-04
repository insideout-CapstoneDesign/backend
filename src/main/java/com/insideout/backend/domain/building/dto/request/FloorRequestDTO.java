package com.insideout.backend.domain.building.dto.request;

import jakarta.validation.constraints.NotBlank;

public record FloorRequestDTO(
        int level,
        @NotBlank String name
) {}
