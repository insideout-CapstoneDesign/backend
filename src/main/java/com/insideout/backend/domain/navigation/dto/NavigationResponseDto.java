package com.insideout.backend.domain.navigation.dto;

import com.insideout.backend.domain.map.enums.MapType;

import java.util.List;
import java.util.UUID;

public record NavigationResponseDto(
        CoordinateDto requestedDestination,
        CoordinateDto routedDestination,
        IndoorInfoDto indoor,
        List<RouteDto> routes,
        List<RouteMode> notFoundRouteTypes,
        List<RouteFailureDto> failures,
        String message
) {
    public NavigationResponseDto(
            CoordinateDto requestedDestination,
            CoordinateDto routedDestination,
            IndoorInfoDto indoor,
            List<RouteDto> routes,
            List<RouteMode> notFoundRouteTypes,
            String message
    ) {
        this(requestedDestination, routedDestination, indoor, routes, notFoundRouteTypes, List.of(), message);
    }

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
            List<LegDto> legs,
            List<RouteFailureDto> failures
    ) {
        public RouteDto(
                RouteMode routeType,
                RouteOption routeOption,
                Integer totalTimeSeconds,
                Integer totalDistanceMeters,
                String totalDuration,
                List<LegDto> legs
        ) {
            this(routeType, routeOption, totalTimeSeconds, totalDistanceMeters, totalDuration, legs, List.of());
        }
    }

    public record RouteFailureDto(
            String code,
            String message,
            RouteMode routeMode,
            RouteOption routeOption,
            LegMode legMode,
            MapType mapType
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
            MapType mapType,
            String mapImageUrl,
            UUID floorId,
            String floorName,
            CoordinateType coordinateType,
            List<CoordinateDto> path,
            List<FloorSegmentDto> floorSegments,
            List<StepDto> steps
    ) {
        public LegDto(
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
            this(mode, routeName, transitType, durationSeconds, distanceMeters, stationCount,
                    startName, endName, null, null, null, null, null, null, List.of(), steps);
        }
    }

    public record FloorSegmentDto(
            MapType mapType,
            UUID floorId,
            String floorName,
            String mapImageUrl,
            CoordinateType coordinateType,
            List<CoordinateDto> path,
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

    public enum CoordinateType {
        WGS84,
        PIXEL
    }
}
