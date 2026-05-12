package com.insideout.backend.domain.navigation.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record NavigationRequestDto(
        @NotNull Double startX,
        @NotNull Double startY,
        @NotNull Double endX,
        @NotNull Double endY,
        String startName,
        String endName,
        UUID destinationBuildingId,
        Boolean includeIndoor,
        List<RouteType> routeTypes
) {

    public enum RouteType {
        TRANSIT,
        CAR,
        WALK
    }
}
