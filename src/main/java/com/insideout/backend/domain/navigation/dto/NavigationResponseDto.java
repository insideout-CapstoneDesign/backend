package com.insideout.backend.domain.navigation.dto;

import java.util.List;
import java.util.UUID;

public record NavigationResponseDto(
        CoordinateDto requestedDestination,
        CoordinateDto routedDestination,
        IndoorInfoDto indoor,
        List<RouteDto> routes,
        List<RouteMode> notFoundRouteTypes,
        String message
) {

    public record CoordinateDto(
            Double x,
            Double y,
            String name
    ) {
    }

    public record IndoorInfoDto(
            Boolean included,
            UUID campusId,
            String campusName,
            String campusEntranceName,
            UUID buildingId,
            String buildingName,
            UUID entranceNodeId,
            String entranceName
    ) {
    }

    public record RouteDto(
            RouteMode routeType,
            RouteOption routeOption,
            Integer totalTimeSeconds,
            Integer totalDistanceMeters,
            String totalDuration,
            List<LegDto> legs
    ) {
    }

    public record LegDto(
            LegMode mode,
            String routeName,
            String transitType,
            Integer durationSeconds,
            Integer distanceMeters,
            Integer stationCount,
            String startName,
            String endName,
            List<StepDto> steps
    ) {
    }

    public record StepDto(
            String instruction,
            Integer distanceMeters,
            Integer durationSeconds,
            Double x,
            Double y,
            Integer turnType,
            String mode,
            String streetName
    ) {
    }

    public enum RouteMode {
        TRANSIT,
        CAR,
        WALK
    }

    public enum RouteOption {
        TRANSIT_CANDIDATE,
        RECOMMENDED,
        MIN_TIME,
        SHORTEST,
        COMFORTABLE
    }

    public enum LegMode {
        WALK,
        BUS,
        SUBWAY,
        CAR,
        CAMPUS,
        INDOOR,
        OTHER
    }
}
