package com.insideout.backend.domain.building.dto.request;

import com.insideout.backend.domain.building.dto.CoordinateDTO;
import com.insideout.backend.domain.building.dto.CampusGateDTO;
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
        @Valid CoordinateDTO primaryEntrance,
        String primaryEntranceName,
        List<@NotNull @Valid CampusGateDTO> gates,
        Boolean requiresFloorplan,
        Map<String, Object> meta
) {}
