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
        Long startPoiId,
        UUID destinationBuildingId,
        Long destinationPoiId,
        Boolean includeIndoor,
        List<RouteType> routeTypes
) {

    public NavigationRequestDto(
            Double startX,
            Double startY,
            Double endX,
            Double endY,
            String startName,
            String endName,
            UUID destinationBuildingId,
            Boolean includeIndoor,
            List<RouteType> routeTypes
    ) {
        this(startX, startY, endX, endY, startName, endName, null, destinationBuildingId, null, includeIndoor, routeTypes);
    }

    public NavigationRequestDto(
            Double startX,
            Double startY,
            Double endX,
            Double endY,
            String startName,
            String endName,
            UUID destinationBuildingId,
            Long destinationPoiId,
            Boolean includeIndoor,
            List<RouteType> routeTypes
    ) {
        this(startX, startY, endX, endY, startName, endName, null, destinationBuildingId, destinationPoiId, includeIndoor, routeTypes);
    }

    public enum RouteType {
        TRANSIT,
        CAR,
        WALK
    }
}
