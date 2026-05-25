package com.insideout.backend.domain.building.dto.request;

import com.insideout.backend.domain.building.dto.CoordinateDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public record CampusCreateRequestDTO(
        @NotBlank String name,
        String address,
        List<@NotNull @Valid CoordinateDTO> boundary,
        @Valid CoordinateDTO centroid,
        @NotNull @Valid CoordinateDTO primaryEntrance,
        String primaryEntranceName,
        Map<String, Object> meta
) {}
