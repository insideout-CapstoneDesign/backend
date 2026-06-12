package com.insideout.backend.domain.navigation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.insideout.backend.domain.map.facade.MapQueryFacade;
import com.insideout.backend.domain.map.facade.MapQueryFacade.IndoorDestinationAnchor;
import com.insideout.backend.domain.map.facade.MapQueryFacade.IndoorPoiDestination;
import com.insideout.backend.domain.map.facade.MapQueryFacade.RoutingEdge;
import com.insideout.backend.domain.map.facade.MapQueryFacade.RoutingGraph;
import com.insideout.backend.domain.map.facade.MapQueryFacade.RoutingNode;
import com.insideout.backend.domain.map.facade.MapQueryFacade.RoutingObstacle;
import com.insideout.backend.domain.map.facade.MapQueryFacade.VerticalRoutingLink;
import com.insideout.backend.domain.map.enums.MapType;
import com.insideout.backend.domain.navigation.dto.NavigationRequestDto;
import com.insideout.backend.domain.navigation.dto.NavigationRequestDto.RouteType;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.CoordinateType;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.CoordinateDto;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.FloorSegmentDto;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.FloorplanDto;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.IndoorInfoDto;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.LegDto;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.LegMode;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.RouteDto;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.RouteFailureDto;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.RouteMode;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.RouteOption;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.StepDto;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.TransitStopDto;
import com.insideout.backend.domain.navigation.exception.NavigationErrorCode;
import com.insideout.backend.domain.navigation.exception.NavigationException;
import com.insideout.backend.global.apiPayload.code.BaseErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

/**
 * 실내외 길찾기(Navigation) 기능을 담당하는 핵심 서비스.
 * Repository를 직접 참조하지 않고, MapQueryFacade 등
 * 다른 도메인의 Service를 주입받아 필요한 데이터를 가져옵니다.
 */
@Service
@Transactional(readOnly = true)
@Slf4j
public class NavigationService {

    private static final String TMAP_TRANSIT_ROUTES_URL = "https://apis.openapi.sk.com/transit/routes";
    private static final String TMAP_CAR_ROUTES_URL = "https://apis.openapi.sk.com/tmap/routes?version=1&format=json";
    private static final String TMAP_WALK_ROUTES_URL = "https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1";
    private static final double COMFORTABLE_STAIR_PENALTY_MULTIPLIER = 20.0;
    private static final double COMFORTABLE_OBSTACLE_PENALTY_MULTIPLIER = 10.0;
    private static final double BLOCKING_EDGE_COST = Double.POSITIVE_INFINITY;
    private static final double INDOOR_INSTRUCTION_CLUSTER_DISTANCE_PX = 260.0;
    private static final double INDOOR_STRAIGHT_ANGLE_DEGREES = 35.0;

    private final MapQueryFacade mapQueryFacade;
    private final RestTemplate restTemplate;

    @Value("${tmap.api.key}")
    private String tmapApiKey;

    public NavigationService(
            MapQueryFacade mapQueryFacade,
            @Qualifier("tmapRestTemplate") RestTemplate restTemplate
    ) {
        this.mapQueryFacade = mapQueryFacade;
        this.restTemplate = restTemplate;
    }

    public NavigationResponseDto findRoutes(NavigationRequestDto request) {
        RouteTarget target = resolveRouteTarget(request);
        EnumSet<RouteType> routeTypes = resolveRouteTypes(request);
        if (target.isIndoorOnly()) {
            return findIndoorOnlyRoutes(request, target, routeTypes);
        }
        List<RouteDto> routes = new ArrayList<>();
        List<RouteMode> notFoundRouteTypes = new ArrayList<>();
        List<RouteFailureDto> failures = new ArrayList<>();

        if (routeTypes.contains(RouteType.TRANSIT)) {
            try {
                List<RouteDto> transitRoutes = findTransitRouteDtos(request, target);
                if (transitRoutes.isEmpty()) {
                    notFoundRouteTypes.add(RouteMode.TRANSIT);
                    failures.add(routeFailure(NavigationErrorCode.ROUTE_NOT_FOUND, RouteMode.TRANSIT, RouteOption.TRANSIT_CANDIDATE, null, null));
                }
                routes.addAll(transitRoutes);
            } catch (NavigationException e) {
                log.warn("TRANSIT route lookup failed.", e);
                notFoundRouteTypes.add(RouteMode.TRANSIT);
                failures.add(routeFailure(e.getErrorCode(), RouteMode.TRANSIT, RouteOption.TRANSIT_CANDIDATE, null, null));
            }
        }
        if (routeTypes.contains(RouteType.CAR)) {
            int beforeSize = routes.size();
            try {
                findCarRouteDto(request, target, RouteOption.RECOMMENDED, 0).ifPresent(routes::add);
                findCarRouteDto(request, target, RouteOption.MIN_TIME, 2).ifPresent(routes::add);
            } catch (NavigationException e) {
                log.warn("CAR route lookup failed.", e);
            }
            if (routes.size() == beforeSize) {
                notFoundRouteTypes.add(RouteMode.CAR);
                failures.add(routeFailure(NavigationErrorCode.ROUTE_NOT_FOUND, RouteMode.CAR, null, null, null));
            }
        }
        if (routeTypes.contains(RouteType.WALK)) {
            int beforeSize = routes.size();
            try {
                findWalkRouteDto(request, target, RouteOption.SHORTEST, "10").ifPresent(routes::add);
                findWalkRouteDto(request, target, RouteOption.COMFORTABLE, "30").ifPresent(routes::add);
            } catch (NavigationException e) {
                log.warn("WALK route lookup failed.", e);
            }
            if (routes.size() == beforeSize) {
                notFoundRouteTypes.add(RouteMode.WALK);
                failures.add(routeFailure(NavigationErrorCode.ROUTE_NOT_FOUND, RouteMode.WALK, null, null, null));
            }
        }

        routes.stream()
                .flatMap(route -> route.failures().stream())
                .forEach(failures::add);

        return new NavigationResponseDto(
                new CoordinateDto(request.endX(), request.endY(), request.endName()),
                new CoordinateDto(target.endX(), target.endY(), target.endName()),
                toIndoorInfo(target),
                routes,
                notFoundRouteTypes,
                failures,
                resolveRouteMessage(routes, notFoundRouteTypes, failures)
        );
    }

    private RouteFailureDto routeFailure(
            BaseErrorCode errorCode,
            RouteMode routeMode,
            RouteOption routeOption,
            LegMode legMode,
            MapType mapType
    ) {
        return new RouteFailureDto(
                errorCode.getCode(),
                errorCode.getMessage(),
                routeMode,
                routeOption,
                legMode,
                mapType
        );
    }

    private IndoorInfoDto toIndoorInfo(RouteTarget target) {
        if (!target.includesIndoor()) {
            return new IndoorInfoDto(false, null, null, null, null, null, null, null, List.of());
        }

        UUID buildingId = target.indoorBuildingId();
        var floorplans = mapQueryFacade.findCurrentBuildingFloorplans(buildingId);
        return new IndoorInfoDto(
                true,
                target.campusId(),
                target.campusName(),
                target.campusEntranceName(),
                buildingId,
                target.buildingName(),
                target.anchor() == null ? null : target.anchor().entranceNodeId(),
                target.entranceName(),
                (floorplans == null ? List.<MapQueryFacade.PublishedFloorplan>of() : floorplans).stream()
                        .map(floorplan -> new FloorplanDto(
                                floorplan.floorId(),
                                floorplan.floorName(),
                                floorplan.mapImageUrl(),
                                CoordinateType.PIXEL
                        ))
                        .toList()
        );
    }

    private String resolveRouteMessage(List<RouteDto> routes, List<RouteMode> notFoundRouteTypes, List<RouteFailureDto> failures) {
        if (notFoundRouteTypes.isEmpty() && failures.isEmpty()) {
            return null;
        }
        if (routes.isEmpty()) {
            return "경로를 찾을 수 없습니다.";
        }
        if (!failures.isEmpty() && failures.stream().anyMatch(failure -> failure.legMode() == LegMode.INDOOR || failure.legMode() == LegMode.CAMPUS)) {
            return "일부 실내 경로를 찾을 수 없습니다.";
        }
        return "일부 이동 수단의 경로를 찾을 수 없습니다.";
    }

    public NavigationResponseDto findTransitRoutes(NavigationRequestDto request) {
        NavigationRequestDto transitOnlyRequest = new NavigationRequestDto(
                request.startX(),
                request.startY(),
                request.endX(),
                request.endY(),
                request.startName(),
                request.endName(),
                request.startPoiId(),
                request.destinationBuildingId(),
                request.destinationPoiId(),
                request.includeIndoor(),
                List.of(RouteType.TRANSIT)
        );
        return findRoutes(transitOnlyRequest);
    }

    private RouteTarget resolveRouteTarget(NavigationRequestDto request) {
        if (Boolean.FALSE.equals(request.includeIndoor())) {
            return RouteTarget.outdoorOnly(request.endX(), request.endY(), request.endName());
        }

        Optional<IndoorPoiDestination> source = mapQueryFacade.findIndoorPoiDestination(request.startPoiId());
        if (source.isPresent()) {
            Optional<IndoorPoiDestination> destination = request.destinationPoiId() == null
                    ? Optional.empty()
                    : mapQueryFacade.findIndoorPoiDestination(request.destinationPoiId());
            if (destination.isPresent() && sameId(source.get().buildingId(), destination.get().buildingId())) {
                return RouteTarget.fromIndoorToIndoor(
                        source.get(),
                        destination.get(),
                        request.endX(),
                        request.endY(),
                        request.endName()
                );
            }

            Optional<IndoorDestinationAnchor> exitAnchor = mapQueryFacade.findIndoorDestinationAnchor(
                    source.get().buildingId(),
                    request.endX(),
                    request.endY(),
                    request.endX(),
                    request.endY()
            );

            return exitAnchor
                    .map(value -> RouteTarget.fromIndoorToOutdoor(
                            value,
                            source.get(),
                            request.endX(),
                            request.endY(),
                            request.endName()
                    ))
                    .orElseGet(() -> RouteTarget.outdoorOnly(request.endX(), request.endY(), request.endName()));
        }

        Optional<IndoorPoiDestination> destination = mapQueryFacade.findIndoorPoiDestination(request.destinationPoiId());
        if (destination.isEmpty()) {
            return RouteTarget.outdoorOnly(request.endX(), request.endY(), request.endName());
        }

        Optional<IndoorDestinationAnchor> anchor = mapQueryFacade.findIndoorDestinationAnchor(
                destination.get().buildingId(),
                request.endX(),
                request.endY(),
                request.startX(),
                request.startY()
        );

        return anchor
                .map(value -> RouteTarget.withIndoor(
                        value,
                        destination.get(),
                        request.endX(),
                        request.endY(),
                        request.endName()
                ))
                .orElseGet(() -> RouteTarget.outdoorOnly(request.endX(), request.endY(), request.endName()));
    }

    private NavigationResponseDto findIndoorOnlyRoutes(
            NavigationRequestDto request,
            RouteTarget target,
            EnumSet<RouteType> routeTypes
    ) {
        List<RouteDto> routes = new ArrayList<>();
        List<RouteFailureDto> failures = new ArrayList<>();

        if (routeTypes.contains(RouteType.WALK)) {
            createIndoorOnlyRouteDto(target, RouteOption.SHORTEST, failures).ifPresent(routes::add);
            createIndoorOnlyRouteDto(target, RouteOption.COMFORTABLE, failures).ifPresent(routes::add);
        } else {
            createIndoorOnlyRouteDto(target, RouteOption.SHORTEST, failures).ifPresent(routes::add);
        }

        if (routes.isEmpty() && failures.isEmpty()) {
            failures.add(routeFailure(NavigationErrorCode.INDOOR_ROUTE_NOT_FOUND, RouteMode.WALK, RouteOption.SHORTEST, LegMode.INDOOR, MapType.BUILDING));
        }

        List<RouteMode> notFoundRouteTypes = routes.isEmpty() ? List.of(RouteMode.WALK) : List.of();
        return new NavigationResponseDto(
                new CoordinateDto(request.endX(), request.endY(), request.endName()),
                new CoordinateDto(request.endX(), request.endY(), target.destination().name()),
                toIndoorInfo(target),
                routes,
                notFoundRouteTypes,
                failures,
                resolveRouteMessage(routes, notFoundRouteTypes, failures)
        );
    }

    private Optional<RouteDto> createIndoorOnlyRouteDto(
            RouteTarget target,
            RouteOption routeOption,
            List<RouteFailureDto> failures
    ) {
        Optional<LegDto> indoorLeg = createIndoorPointToPointLeg(target, routeOption);
        if (indoorLeg.isEmpty()) {
            failures.add(routeFailure(NavigationErrorCode.INDOOR_ROUTE_NOT_FOUND, RouteMode.WALK, routeOption, LegMode.INDOOR, MapType.BUILDING));
            return Optional.empty();
        }

        return Optional.of(new RouteDto(
                RouteMode.WALK,
                routeOption,
                null,
                null,
                formatDuration(null, true),
                List.of(indoorLeg.get())
        ));
    }

    private boolean sameId(UUID first, UUID second) {
        return first != null && first.equals(second);
    }

    private EnumSet<RouteType> resolveRouteTypes(NavigationRequestDto request) {
        if (request.routeTypes() == null || request.routeTypes().isEmpty()) {
            return EnumSet.allOf(RouteType.class);
        }

        EnumSet<RouteType> routeTypes = EnumSet.noneOf(RouteType.class);
        routeTypes.addAll(request.routeTypes());
        return routeTypes;
    }

    private List<RouteDto> findTransitRouteDtos(NavigationRequestDto request, RouteTarget target) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("startX", target.outdoorStartX(request));
        body.put("startY", target.outdoorStartY(request));
        body.put("endX", target.endX());
        body.put("endY", target.endY());
        body.put("format", "json");
        body.put("count", 10);

        JsonNode response = postForJson(TMAP_TRANSIT_ROUTES_URL, body);
        return parseTransitRoutes(response, target);
    }

    private Optional<RouteDto> findCarRouteDto(
            NavigationRequestDto request,
            RouteTarget target,
            RouteOption routeOption,
            int searchOption
    ) {
        Map<String, Object> body = baseOutdoorRouteBody(request, target);
        body.put("searchOption", searchOption);
        body.put("carType", 1);
        body.put("totalValue", 1);
        body.put("trafficInfo", "N");

        JsonNode response = postForJson(TMAP_CAR_ROUTES_URL, body);
        return parseFeatureCollectionRoute(response, RouteMode.CAR, routeOption, LegMode.CAR, target);
    }

    private Optional<RouteDto> findWalkRouteDto(
            NavigationRequestDto request,
            RouteTarget target,
            RouteOption routeOption,
            String searchOption
    ) {
        Map<String, Object> body = baseOutdoorRouteBody(request, target);
        body.put("searchOption", searchOption);
        body.put("sort", "index");

        JsonNode response = postForJson(TMAP_WALK_ROUTES_URL, body);
        return parseFeatureCollectionRoute(response, RouteMode.WALK, routeOption, LegMode.WALK, target);
    }

    private Map<String, Object> baseOutdoorRouteBody(NavigationRequestDto request, RouteTarget target) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("startX", target.outdoorStartX(request));
        body.put("startY", target.outdoorStartY(request));
        body.put("endX", target.endX());
        body.put("endY", target.endY());
        body.put("reqCoordType", "WGS84GEO");
        body.put("resCoordType", "WGS84GEO");
        body.put("startName", defaultName(target.outdoorStartName(request), "출발"));
        body.put("endName", defaultName(target.endName(), "도착"));
        return body;
    }

    private JsonNode postForJson(String url, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("appKey", normalizeApiKey(tmapApiKey));

        try {
            JsonNode response = restTemplate.postForObject(url, new HttpEntity<>(body, headers), JsonNode.class);
            if (response == null || response.isNull()) {
                log.warn("TMAP API returned empty response. url={}", url);
                throw new NavigationException(NavigationErrorCode.TMAP_EMPTY_RESPONSE);
            }
            return response;
        } catch (RestClientResponseException e) {
            log.warn(
                    "TMAP API returned error. url={}, status={}",
                    url,
                    e.getStatusCode()
            );
            throw new NavigationException(NavigationErrorCode.TMAP_REQUEST_FAILED, e);
        } catch (RestClientException e) {
            log.warn("TMAP API request failed. url={}", url, e);
            throw new NavigationException(NavigationErrorCode.TMAP_CONNECTION_FAILED, e);
        }
    }

    private String normalizeApiKey(String apiKey) {
        String normalized = apiKey == null ? "" : apiKey.trim();
        if (normalized.length() >= 2) {
            char first = normalized.charAt(0);
            char last = normalized.charAt(normalized.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                normalized = normalized.substring(1, normalized.length() - 1).trim();
            }
        }
        if (normalized.isBlank()) {
            throw new IllegalStateException("tmap.api.key must not be blank");
        }
        return normalized;
    }

    private List<RouteDto> parseTransitRoutes(JsonNode response, RouteTarget target) {
        JsonNode itineraries = response
                .path("metaData")
                .path("plan")
                .path("itineraries");

        if (!itineraries.isArray()) {
            log.warn("TMAP transit response has invalid structure.");
            throw new NavigationException(NavigationErrorCode.INVALID_TRANSIT_RESPONSE);
        }
        if (itineraries.isEmpty()) {
            return List.of();
        }

        List<RouteDto> routes = new ArrayList<>();
        for (JsonNode itinerary : itineraries) {
            Integer totalTimeSeconds = getNullableInt(itinerary.path("totalTime"));
            Integer totalDistanceMeters = getNullableInt(itinerary.path("totalDistance"));
            List<LegDto> legs = normalizeOutdoorBoundaryLegs(parseTransitLegs(itinerary.path("legs")), target);
            List<RouteFailureDto> failures = new ArrayList<>();
            applyHybridLegs(legs, failures, target, RouteMode.TRANSIT, RouteOption.TRANSIT_CANDIDATE);

            routes.add(new RouteDto(
                    RouteMode.TRANSIT,
                    RouteOption.TRANSIT_CANDIDATE,
                    totalTimeSeconds,
                    totalDistanceMeters,
                    formatDuration(totalTimeSeconds, target.includesIndoor()),
                    legs,
                    failures
            ));
        }

        return routes;
    }

    private List<LegDto> parseTransitLegs(JsonNode legsNode) {
        List<LegDto> legs = new ArrayList<>();

        if (!legsNode.isArray()) {
            return legs;
        }

        for (JsonNode legNode : legsNode) {
            LegMode mode = parseTransitLegMode(getNullableText(legNode.path("mode")));
            List<TransitStopDto> stops = parseTransitStops(legNode);
            List<CoordinateDto> path = parseTransitPath(legNode);
            legs.add(new LegDto(
                    mode,
                    resolveTransitRouteName(legNode),
                    getNullableText(legNode.path("type")),
                    getNullableInt(legNode.path("sectionTime")),
                    getNullableInt(legNode.path("distance")),
                    firstNonNull(countTransitStations(legNode), stops.isEmpty() ? null : stops.size()),
                    stops,
                    getNullableText(legNode.path("start").path("name")),
                    getNullableText(legNode.path("end").path("name")),
                    null,
                    null,
                    null,
                    null,
                    path.isEmpty() ? null : CoordinateType.WGS84,
                    path,
                    List.of(),
                    parseTransitSteps(legNode)
            ));
        }

        return legs;
    }

    private List<CoordinateDto> parseTransitPath(JsonNode legNode) {
        List<CoordinateDto> path = new ArrayList<>();
        JsonNode stepsNode = legNode.path("steps");

        if (stepsNode.isArray()) {
            for (JsonNode stepNode : stepsNode) {
                appendLinestringCoordinates(path, stepNode.path("linestring"));
            }
        }

        if (path.size() < 2) {
            path.clear();
            appendTransitPointCoordinate(path, legNode.path("start"));
            appendTransitStationCoordinates(path, transitStationListNode(legNode));
            appendTransitPointCoordinate(path, legNode.path("end"));
        }

        return path;
    }

    private void appendTransitStationCoordinates(List<CoordinateDto> path, JsonNode stationList) {
        if (!stationList.isArray()) {
            return;
        }

        for (JsonNode stationNode : stationList) {
            appendTransitPointCoordinate(path, stationNode);
        }
    }

    private void appendLinestringCoordinates(List<CoordinateDto> path, JsonNode linestringNode) {
        String linestring = getNullableText(linestringNode);
        if (linestring == null || linestring.isBlank()) {
            return;
        }

        for (String pair : linestring.trim().split("\\s+")) {
            String[] coordinates = pair.split(",");
            if (coordinates.length < 2) {
                continue;
            }

            try {
                appendCoordinate(
                        path,
                        Double.parseDouble(coordinates[0]),
                        Double.parseDouble(coordinates[1])
                );
            } catch (NumberFormatException ignored) {
                // Skip malformed pairs from the external API and keep the rest of the path.
            }
        }
    }

    private void appendTransitPointCoordinate(List<CoordinateDto> path, JsonNode pointNode) {
        Double x = firstNonNull(
                getNullableDouble(pointNode.path("lon")),
                firstNonNull(
                        getNullableDouble(pointNode.path("x")),
                        firstNonNull(
                                getNullableDouble(pointNode.path("stationX")),
                                getNullableDouble(pointNode.path("stopX"))
                        )
                )
        );
        Double y = firstNonNull(
                getNullableDouble(pointNode.path("lat")),
                firstNonNull(
                        getNullableDouble(pointNode.path("y")),
                        firstNonNull(
                                getNullableDouble(pointNode.path("stationY")),
                                getNullableDouble(pointNode.path("stopY"))
                        )
                )
        );

        if (x == null || y == null) {
            return;
        }

        appendCoordinate(path, x, y);
    }

    private List<StepDto> parseTransitSteps(JsonNode legNode) {
        List<StepDto> steps = new ArrayList<>();
        JsonNode stepsNode = legNode.path("steps");

        if (stepsNode.isArray()) {
            for (JsonNode stepNode : stepsNode) {
                CoordinateDto coordinate = firstLinestringCoordinate(stepNode.path("linestring"));
                steps.add(new StepDto(
                        firstText(stepNode, "description", "desc", "instruction"),
                        getNullableInt(stepNode.path("distance")),
                        getNullableInt(stepNode.path("sectionTime")),
                        firstNonNull(getNullableDouble(stepNode.path("lon")), coordinate.x()),
                        firstNonNull(getNullableDouble(stepNode.path("lat")), coordinate.y()),
                        getNullableInt(stepNode.path("turnType")),
                        getNullableText(legNode.path("mode")),
                        firstText(stepNode, "streetName", "name")
                ));
            }
        }

        if (!steps.isEmpty()) {
            return steps;
        }

        String startName = getNullableText(legNode.path("start").path("name"));
        String endName = getNullableText(legNode.path("end").path("name"));
        Integer distance = getNullableInt(legNode.path("distance"));
        if (startName != null || endName != null || distance != null) {
            steps.add(new StepDto(
                    buildTransitInstruction(getNullableText(legNode.path("mode")), startName, endName, distance),
                    distance,
                    getNullableInt(legNode.path("sectionTime")),
                    null,
                    null,
                    null,
                    getNullableText(legNode.path("mode")),
                    null
            ));
        }

        return steps;
    }

    private Optional<RouteDto> parseFeatureCollectionRoute(
            JsonNode response,
            RouteMode routeMode,
            RouteOption routeOption,
            LegMode legMode,
            RouteTarget target
    ) {
        JsonNode features = response.path("features");
        if (!features.isArray()) {
            log.warn("TMAP {} response has invalid structure.", routeMode);
            throw new NavigationException(resolveInvalidFeatureResponseErrorCode(routeMode));
        }
        if (features.isEmpty()) {
            return Optional.empty();
        }

        JsonNode summary = features.get(0).path("properties");
        Integer totalTimeSeconds = getNullableInt(summary.path("totalTime"));
        Integer totalDistanceMeters = getNullableInt(summary.path("totalDistance"));
        List<StepDto> steps = rewriteOutdoorArrivalSteps(parseFeatureSteps(features), target);
        List<CoordinateDto> path = normalizeOutdoorBoundaryPath(parseFeaturePath(features), target);
        List<LegDto> legs = new ArrayList<>();
        List<RouteFailureDto> failures = new ArrayList<>();
        prependExitLegsIfNeeded(legs, failures, target, routeMode, routeOption);
        legs.add(new LegDto(
                legMode,
                null,
                null,
                totalTimeSeconds,
                totalDistanceMeters,
                null,
                List.of(),
                null,
                target.endName(),
                null,
                null,
                null,
                null,
                CoordinateType.WGS84,
                path,
                List.of(),
                steps
        ));
        appendEntryLegsIfNeeded(legs, failures, target, routeMode, routeOption);

        return Optional.of(new RouteDto(
                routeMode,
                routeOption,
                totalTimeSeconds,
                totalDistanceMeters,
                formatDuration(totalTimeSeconds, target.includesIndoor()),
                legs,
                failures
        ));
    }

    private NavigationErrorCode resolveInvalidFeatureResponseErrorCode(RouteMode routeMode) {
        return switch (routeMode) {
            case CAR -> NavigationErrorCode.INVALID_CAR_RESPONSE;
            case WALK -> NavigationErrorCode.INVALID_WALK_RESPONSE;
            default -> NavigationErrorCode.TMAP_REQUEST_FAILED;
        };
    }

    private List<StepDto> parseFeatureSteps(JsonNode features) {
        List<StepDto> steps = new ArrayList<>();

        for (JsonNode feature : features) {
            JsonNode properties = feature.path("properties");
            String description = getNullableText(properties.path("description"));
            if (description == null || description.isBlank()) {
                continue;
            }

            CoordinateDto coordinate = firstPointCoordinate(feature.path("geometry").path("coordinates"));
            steps.add(new StepDto(
                    description,
                    getNullableInt(properties.path("distance")),
                    getNullableInt(properties.path("time")),
                    coordinate.x(),
                    coordinate.y(),
                    getNullableInt(properties.path("turnType")),
                    getNullableText(properties.path("facilityType")),
                    getNullableText(properties.path("name"))
            ));
        }

        return steps;
    }

    private List<StepDto> rewriteOutdoorArrivalSteps(List<StepDto> steps, RouteTarget target) {
        if (!target.includesIndoor() || target.startsIndoor() || steps.isEmpty()) {
            return steps;
        }

        List<StepDto> rewritten = new ArrayList<>(steps);
        for (int index = rewritten.size() - 1; index >= 0; index--) {
            StepDto step = rewritten.get(index);
            if (!normalizeInstruction(step.instruction()).contains("도착")) {
                continue;
            }
            rewritten.set(index, rewriteOutdoorBoundaryArrivalStep(step, target));
            return rewritten;
        }

        int lastIndex = rewritten.size() - 1;
        rewritten.set(lastIndex, rewriteOutdoorBoundaryArrivalStep(rewritten.get(lastIndex), target));
        return rewritten;
    }

    private StepDto rewriteOutdoorBoundaryArrivalStep(StepDto step, RouteTarget target) {
        return new StepDto(
                outdoorBoundaryArrivalInstruction(target),
                step.distanceMeters(),
                step.durationSeconds(),
                target.endX(),
                target.endY(),
                step.turnType(),
                "BUILDING",
                target.endName(),
                step.pathStartIndex(),
                step.pathEndIndex()
        );
    }

    private String indoorEntryInstruction(RouteTarget target) {
        String buildingName = target.buildingName();
        return (buildingName == null || buildingName.isBlank() ? "건물" : buildingName) + " 입구 진입";
    }

    private String outdoorBoundaryArrivalInstruction(RouteTarget target) {
        String entranceName = target.entranceName();
        String buildingName = target.buildingName();
        if (entranceName != null && !entranceName.isBlank()) {
            if (buildingName != null && !buildingName.isBlank() && !sameText(buildingName, entranceName)) {
                return buildingName + " " + entranceName + " 도착";
            }
            return entranceName + " 도착";
        }

        String boundaryName = target.endName();
        if (boundaryName == null || boundaryName.isBlank()) {
            boundaryName = buildingName;
        }
        if (sameText(boundaryName, buildingName)) {
            boundaryName = boundaryName + " 출입구";
        }
        return (boundaryName == null || boundaryName.isBlank() ? "출입구" : boundaryName) + " 도착";
    }

    private boolean sameText(String first, String second) {
        return normalizeInstruction(first).equals(normalizeInstruction(second));
    }

    private String indoorExitInstruction(RouteTarget target) {
        String buildingName = target.buildingName();
        String safeBuildingName = buildingName == null || buildingName.isBlank() ? "건물" : buildingName;
        return safeBuildingName + " 출구로 나가기";
    }

    private List<CoordinateDto> parseFeaturePath(JsonNode features) {
        List<CoordinateDto> path = new ArrayList<>();

        for (JsonNode feature : features) {
            appendGeometryCoordinates(path, feature.path("geometry").path("coordinates"));
        }

        return path;
    }

    private List<LegDto> normalizeOutdoorBoundaryLegs(List<LegDto> legs, RouteTarget target) {
        if (legs.isEmpty() || !target.includesIndoor()) {
            return legs;
        }

        List<LegDto> normalized = new ArrayList<>(legs);
        if (target.startsIndoor()) {
            int firstOutdoorLegIndex = firstOutdoorLegIndex(normalized);
            if (firstOutdoorLegIndex >= 0) {
                LegDto leg = normalized.get(firstOutdoorLegIndex);
                normalized.set(firstOutdoorLegIndex, withPath(leg, normalizeOutdoorBoundaryPath(leg.path(), target)));
            }
            return normalized;
        }

        int lastOutdoorLegIndex = lastOutdoorLegIndex(normalized);
        if (lastOutdoorLegIndex >= 0) {
            LegDto leg = normalized.get(lastOutdoorLegIndex);
            normalized.set(lastOutdoorLegIndex, withPath(leg, normalizeOutdoorBoundaryPath(leg.path(), target)));
        }
        return normalized;
    }

    private int firstOutdoorLegIndex(List<LegDto> legs) {
        for (int index = 0; index < legs.size(); index++) {
            if (isOutdoorLeg(legs.get(index))) {
                return index;
            }
        }
        return -1;
    }

    private int lastOutdoorLegIndex(List<LegDto> legs) {
        for (int index = legs.size() - 1; index >= 0; index--) {
            if (isOutdoorLeg(legs.get(index))) {
                return index;
            }
        }
        return -1;
    }

    private boolean isOutdoorLeg(LegDto leg) {
        return leg != null && leg.coordinateType() == CoordinateType.WGS84;
    }

    private LegDto withPath(LegDto leg, List<CoordinateDto> path) {
        return new LegDto(
                leg.mode(),
                leg.routeName(),
                leg.transitType(),
                leg.durationSeconds(),
                leg.distanceMeters(),
                leg.stationCount(),
                leg.stops(),
                leg.startName(),
                leg.endName(),
                leg.mapType(),
                leg.mapImageUrl(),
                leg.floorId(),
                leg.floorName(),
                leg.coordinateType(),
                path,
                leg.floorSegments(),
                leg.steps()
        );
    }

    private List<CoordinateDto> normalizeOutdoorBoundaryPath(List<CoordinateDto> path, RouteTarget target) {
        if (!target.includesIndoor()) {
            return path;
        }

        CoordinateDto boundary = target.startsIndoor()
                ? new CoordinateDto(target.resolvedOutdoorStartX(), target.resolvedOutdoorStartY(), target.resolvedOutdoorStartName())
                : new CoordinateDto(target.endX(), target.endY(), target.endName());
        if (boundary.x() == null || boundary.y() == null) {
            return path;
        }

        List<CoordinateDto> normalized = path == null ? new ArrayList<>() : new ArrayList<>(path);
        if (normalized.isEmpty()) {
            normalized.add(boundary);
            return normalized;
        }

        if (target.startsIndoor()) {
            normalized.set(0, boundary);
        } else {
            normalized.set(normalized.size() - 1, boundary);
        }
        return normalized;
    }

    private void appendGeometryCoordinates(List<CoordinateDto> path, JsonNode coordinates) {
        if (!coordinates.isArray() || coordinates.isEmpty()) {
            return;
        }

        if (coordinates.size() >= 2 && coordinates.get(0).isNumber() && coordinates.get(1).isNumber()) {
            appendCoordinate(path, coordinates.get(0).asDouble(), coordinates.get(1).asDouble());
            return;
        }

        for (JsonNode child : coordinates) {
            appendGeometryCoordinates(path, child);
        }
    }

    private void appendCoordinate(List<CoordinateDto> path, double x, double y) {
        if (!isWgs84Coordinate(x, y)) {
            return;
        }
        CoordinateDto last = path.isEmpty() ? null : path.get(path.size() - 1);
        if (last != null && sameCoordinate(last, x, y)) {
            return;
        }
        path.add(new CoordinateDto(x, y, null));
    }

    private boolean sameCoordinate(CoordinateDto coordinate, double x, double y) {
        return Double.compare(coordinate.x(), x) == 0 && Double.compare(coordinate.y(), y) == 0;
    }

    private boolean isWgs84Coordinate(double x, double y) {
        return x >= -180 && x <= 180 && y >= -90 && y <= 90;
    }

    private CoordinateDto firstPointCoordinate(JsonNode coordinates) {
        if (coordinates.isArray() && coordinates.size() >= 2 && coordinates.get(0).isNumber()) {
            return new CoordinateDto(coordinates.get(0).asDouble(), coordinates.get(1).asDouble(), null);
        }
        return new CoordinateDto(null, null, null);
    }

    private void addHybridLegsIfNeeded(List<LegDto> legs, RouteTarget target, RouteOption routeOption) {
        applyHybridLegs(legs, new ArrayList<>(), target, null, routeOption);
    }

    private void applyHybridLegs(
            List<LegDto> legs,
            List<RouteFailureDto> failures,
            RouteTarget target,
            RouteMode routeMode,
            RouteOption routeOption
    ) {
        if (target.startsIndoor()) {
            prependExitLegsIfNeeded(legs, failures, target, routeMode, routeOption);
        } else {
            appendEntryLegsIfNeeded(legs, failures, target, routeMode, routeOption);
        }
    }

    private void appendEntryLegsIfNeeded(
            List<LegDto> legs,
            List<RouteFailureDto> failures,
            RouteTarget target,
            RouteMode routeMode,
            RouteOption routeOption
    ) {
        if (!target.includesIndoor() || target.startsIndoor()) {
            return;
        }

        if (target.hasCampus()) {
            createCampusLeg(target, routeOption).ifPresentOrElse(
                    legs::add,
                    () -> {
                        failures.add(routeFailure(NavigationErrorCode.CAMPUS_ROUTE_NOT_FOUND, routeMode, routeOption, LegMode.CAMPUS, MapType.CAMPUS));
                        legs.add(createFallbackCampusLeg(target));
                    }
            );
        }

        createIndoorLeg(target, routeOption).ifPresentOrElse(
                legs::add,
                () -> {
                    if (!target.hasIndoorDestinationNode()) {
                        legs.add(createFallbackIndoorLeg(target));
                        return;
                    }
                    failures.add(routeFailure(NavigationErrorCode.INDOOR_ROUTE_NOT_FOUND, routeMode, routeOption, LegMode.INDOOR, MapType.BUILDING));
                    legs.add(createFallbackIndoorLeg(target));
                }
        );
    }

    private void prependExitLegsIfNeeded(
            List<LegDto> legs,
            List<RouteFailureDto> failures,
            RouteTarget target,
            RouteMode routeMode,
            RouteOption routeOption
    ) {
        if (!target.startsIndoor()) {
            return;
        }

        List<LegDto> exitLegs = new ArrayList<>();
        createExitIndoorLeg(target, routeOption).ifPresentOrElse(
                exitLegs::add,
                () -> {
                    failures.add(routeFailure(NavigationErrorCode.INDOOR_ROUTE_NOT_FOUND, routeMode, routeOption, LegMode.INDOOR, MapType.BUILDING));
                    exitLegs.add(createFallbackExitIndoorLeg(target));
                }
        );
        if (target.hasCampus()) {
            createExitCampusLeg(target, routeOption).ifPresentOrElse(
                    exitLegs::add,
                    () -> {
                        failures.add(routeFailure(NavigationErrorCode.CAMPUS_ROUTE_NOT_FOUND, routeMode, routeOption, LegMode.CAMPUS, MapType.CAMPUS));
                        exitLegs.add(createFallbackExitCampusLeg(target));
                    }
            );
        }
        legs.addAll(0, exitLegs);
    }

    private Optional<LegDto> createCampusLeg(RouteTarget target, RouteOption routeOption) {
        if (!target.hasCampus()) {
            return Optional.empty();
        }

        Optional<UUID> startNodeId = mapQueryFacade.findNearestPublishedCampusNodeId(
                target.anchor().campusId(),
                target.anchor().campusEntranceX(),
                target.anchor().campusEntranceY()
        );
        Optional<UUID> endNodeId = mapQueryFacade.findNearestPublishedCampusNodeId(
                target.anchor().campusId(),
                target.anchor().x(),
                target.anchor().y()
        );
        Optional<RoutingGraph> graph = mapQueryFacade.findPublishedRoutingGraph(MapType.CAMPUS, target.anchor().campusId());

        if (startNodeId.isEmpty() || endNodeId.isEmpty() || graph.isEmpty()) {
            return Optional.empty();
        }

        return computeIndoorRoute(graph.get(), startNodeId.get(), endNodeId.get(), routeOption)
                .map(route -> toNavigationLeg(
                        LegMode.CAMPUS,
                        MapType.CAMPUS,
                        mapQueryFacade.findCurrentCampusMapImageUrl(target.anchor().campusId()).orElse(graph.get().mapImageUrl()),
                        null,
                        null,
                        target.campusEntranceName(),
                        target.entranceName(),
                        route
                ));
    }

    private Optional<LegDto> createIndoorLeg(RouteTarget target, RouteOption routeOption) {
        if (target.destination() == null) {
            return Optional.empty();
        }

        Optional<RoutingGraph> graph = mapQueryFacade.findPublishedRoutingGraph(MapType.BUILDING, target.destination().buildingId());
        if (graph.isEmpty()) {
            return Optional.empty();
        }

        return computeIndoorRoute(graph.get(), target.anchor().entranceNodeId(), target.destination().anchorNodeId(), routeOption)
                .map(route -> toNavigationLeg(
                        LegMode.INDOOR,
                        MapType.BUILDING,
                        mapQueryFacade.findCurrentFloorplanImageUrl(target.destination().floorId()).orElse(graph.get().mapImageUrl()),
                        target.destination().floorId(),
                        target.destination().floorName(),
                        target.entranceName(),
                        target.destination().name(),
                        route,
                        indoorEntryInstruction(target),
                        target.destination().name() + " 도착"
                ));
    }

    private Optional<LegDto> createExitIndoorLeg(RouteTarget target, RouteOption routeOption) {
        if (target.source() == null || target.anchor() == null) {
            return Optional.empty();
        }

        Optional<RoutingGraph> graph = mapQueryFacade.findPublishedRoutingGraph(MapType.BUILDING, target.source().buildingId());
        if (graph.isEmpty()) {
            return Optional.empty();
        }

        return computeIndoorRoute(graph.get(), target.source().anchorNodeId(), target.anchor().entranceNodeId(), routeOption)
                .map(route -> toNavigationLeg(
                        LegMode.INDOOR,
                        MapType.BUILDING,
                        mapQueryFacade.findCurrentFloorplanImageUrl(target.source().floorId()).orElse(graph.get().mapImageUrl()),
                        target.source().floorId(),
                        target.source().floorName(),
                        target.source().name(),
                        target.entranceName(),
                        route,
                        target.source().name() + "에서 출발",
                        indoorExitInstruction(target)
                ));
    }

    private Optional<LegDto> createIndoorPointToPointLeg(RouteTarget target, RouteOption routeOption) {
        if (target.source() == null || target.destination() == null) {
            return Optional.empty();
        }

        Optional<RoutingGraph> graph = mapQueryFacade.findPublishedRoutingGraph(MapType.BUILDING, target.source().buildingId());
        if (graph.isEmpty()) {
            return Optional.empty();
        }

        return computeIndoorRoute(graph.get(), target.source().anchorNodeId(), target.destination().anchorNodeId(), routeOption)
                .map(route -> toNavigationLeg(
                        LegMode.INDOOR,
                        MapType.BUILDING,
                        mapQueryFacade.findCurrentFloorplanImageUrl(target.source().floorId()).orElse(graph.get().mapImageUrl()),
                        target.source().floorId(),
                        target.source().floorName(),
                        target.source().name(),
                        target.destination().name(),
                        route,
                        target.source().name() + "에서 출발",
                        target.destination().name() + " 도착"
                ));
    }

    private Optional<LegDto> createExitCampusLeg(RouteTarget target, RouteOption routeOption) {
        if (!target.hasCampus()) {
            return Optional.empty();
        }

        Optional<UUID> startNodeId = mapQueryFacade.findNearestPublishedCampusNodeId(
                target.anchor().campusId(),
                target.anchor().x(),
                target.anchor().y()
        );
        Optional<UUID> endNodeId = mapQueryFacade.findNearestPublishedCampusNodeId(
                target.anchor().campusId(),
                target.anchor().campusEntranceX(),
                target.anchor().campusEntranceY()
        );
        Optional<RoutingGraph> graph = mapQueryFacade.findPublishedRoutingGraph(MapType.CAMPUS, target.anchor().campusId());

        if (startNodeId.isEmpty() || endNodeId.isEmpty() || graph.isEmpty()) {
            return Optional.empty();
        }

        return computeIndoorRoute(graph.get(), startNodeId.get(), endNodeId.get(), routeOption)
                .map(route -> toNavigationLeg(
                        LegMode.CAMPUS,
                        MapType.CAMPUS,
                        mapQueryFacade.findCurrentCampusMapImageUrl(target.anchor().campusId()).orElse(graph.get().mapImageUrl()),
                        null,
                        null,
                        target.entranceName(),
                        target.campusEntranceName(),
                        route
                ));
    }

    private LegDto toNavigationLeg(
            LegMode mode,
            MapType mapType,
            String mapImageUrl,
            UUID floorId,
            String floorName,
            String startName,
            String endName,
            ComputedIndoorRoute route
    ) {
        return toNavigationLeg(mode, mapType, mapImageUrl, floorId, floorName, startName, endName, route, null, null);
    }

    private LegDto toNavigationLeg(
            LegMode mode,
            MapType mapType,
            String mapImageUrl,
            UUID floorId,
            String floorName,
            String startName,
            String endName,
            ComputedIndoorRoute route,
            String startInstruction,
            String endInstruction
    ) {
        List<StepDto> rawSteps = rewriteBoundarySteps(route.rawSteps(), startInstruction, endInstruction);
        return new LegDto(
                mode,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                startName,
                endName,
                mapType,
                mapImageUrl,
                floorId,
                floorName,
                CoordinateType.PIXEL,
                route.path(),
                buildFloorSegments(mode, mapType, mapImageUrl, floorId, floorName, route, rawSteps),
                mergeIndoorSteps(rawSteps)
        );
    }

    private List<FloorSegmentDto> buildFloorSegments(
            LegMode mode,
            MapType mapType,
            String fallbackMapImageUrl,
            UUID fallbackFloorId,
            String fallbackFloorName,
            ComputedIndoorRoute route,
            List<StepDto> rawSteps
    ) {
        if (route.nodes().isEmpty()) {
            return List.of();
        }

        if (mapType == MapType.CAMPUS || mode == LegMode.CAMPUS) {
            return List.of(new FloorSegmentDto(
                    mapType,
                    null,
                    null,
                    fallbackMapImageUrl,
                    CoordinateType.PIXEL,
                    route.path(),
                    mergeIndoorSteps(rawSteps)
            ));
        }

        List<FloorSegmentDto> segments = new ArrayList<>();
        Map<UUID, String> floorImageUrlCache = new HashMap<>();
        int startIndex = 0;

        while (startIndex < route.nodes().size()) {
            RoutingNode startNode = route.nodes().get(startIndex);
            UUID segmentFloorId = firstNonNull(startNode.floorId(), fallbackFloorId);
            String segmentFloorName = firstNonNull(startNode.floorName(), fallbackFloorName);
            int endIndex = startIndex + 1;

            while (endIndex < route.nodes().size()
                    && sameFloor(firstNonNull(route.nodes().get(endIndex).floorId(), fallbackFloorId), segmentFloorId)) {
                endIndex++;
            }

            List<RoutingNode> segmentNodes = route.nodes().subList(startIndex, endIndex);
            List<CoordinateDto> segmentPath = segmentNodes.stream()
                    .map(node -> new CoordinateDto(node.x(), node.y(), node.displayName()))
                    .toList();

            segments.add(new FloorSegmentDto(
                    mapType,
                    segmentFloorId,
                    segmentFloorName,
                    resolveSegmentMapImageUrl(segmentFloorId, fallbackMapImageUrl, floorImageUrlCache),
                    CoordinateType.PIXEL,
                    segmentPath,
                    mergeIndoorSteps(stepsForNodeRange(rawSteps, startIndex, endIndex, route.nodes().size()))
            ));

            startIndex = endIndex;
        }

        return segments;
    }

    private String resolveSegmentMapImageUrl(UUID floorId, String fallbackMapImageUrl, Map<UUID, String> cache) {
        if (floorId == null) {
            return fallbackMapImageUrl;
        }
        return cache.computeIfAbsent(
                floorId,
                key -> mapQueryFacade.findCurrentFloorplanImageUrl(key).orElse(fallbackMapImageUrl)
        );
    }

    private List<StepDto> stepsForNodeRange(List<StepDto> steps, int startIndex, int endIndex, int nodeCount) {
        if (steps.isEmpty() || startIndex >= endIndex) {
            return List.of();
        }

        int fromIndex = Math.min(Math.max(startIndex, 0), steps.size());
        int toIndex = Math.min(endIndex, steps.size());
        List<StepDto> segmentSteps = steps.subList(fromIndex, toIndex).stream()
                .map(step -> shiftStepPathRange(step, startIndex))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        int arrivalStepIndex = nodeCount;
        if (endIndex == nodeCount && arrivalStepIndex < steps.size()) {
            segmentSteps.add(shiftStepPathRange(steps.get(arrivalStepIndex), startIndex));
        }

        return segmentSteps;
    }

    private StepDto shiftStepPathRange(StepDto step, int offset) {
        return new StepDto(
                step.instruction(),
                step.distanceMeters(),
                step.durationSeconds(),
                step.x(),
                step.y(),
                step.turnType(),
                step.mode(),
                step.streetName(),
                shiftPathIndex(step.pathStartIndex(), offset),
                shiftPathIndex(step.pathEndIndex(), offset)
        );
    }

    private Integer shiftPathIndex(Integer value, int offset) {
        return value == null ? null : Math.max(value - offset, 0);
    }

    private List<StepDto> rewriteBoundarySteps(List<StepDto> steps, String startInstruction, String endInstruction) {
        if (steps.isEmpty()) {
            return steps;
        }

        List<StepDto> rewritten = new ArrayList<>(steps);
        if (startInstruction != null && !startInstruction.isBlank()) {
            rewritten.set(0, rewriteStepInstruction(rewritten.get(0), startInstruction));
        }
        if (endInstruction != null && !endInstruction.isBlank()) {
            int lastIndex = rewritten.size() - 1;
            rewritten.set(lastIndex, rewriteStepInstruction(rewritten.get(lastIndex), endInstruction));
        }

        return rewritten;
    }

    private StepDto rewriteStepInstruction(StepDto step, String instruction) {
        return new StepDto(
                instruction,
                step.distanceMeters(),
                step.durationSeconds(),
                step.x(),
                step.y(),
                step.turnType(),
                step.mode(),
                step.streetName(),
                step.pathStartIndex(),
                step.pathEndIndex()
        );
    }

    private boolean sameFloor(UUID first, UUID second) {
        return first == null ? second == null : first.equals(second);
    }

    private LegDto createFallbackCampusLeg(RouteTarget target) {
        return new LegDto(
                LegMode.CAMPUS,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                target.campusEntranceName(),
                target.entranceName(),
                MapType.CAMPUS,
                target.anchor() == null ? null : mapQueryFacade.findCurrentCampusMapImageUrl(target.anchor().campusId()).orElse(null),
                null,
                null,
                CoordinateType.PIXEL,
                List.of(),
                List.of(),
                List.of(new StepDto("경로를 찾을 수 없습니다.", null, null, target.buildingEntranceX(), target.buildingEntranceY(), null, "CAMPUS", null))
        );
    }

    private LegDto createFallbackIndoorLeg(RouteTarget target) {
        UUID floorId = target.destination() == null ? null : target.destination().floorId();
        String floorName = target.destination() == null ? null : target.destination().floorName();
        String endName = target.destination() == null ? target.originalEndName() : target.destination().name();

        return new LegDto(
                LegMode.INDOOR,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                target.entranceName(),
                endName,
                MapType.BUILDING,
                floorId == null ? null : mapQueryFacade.findCurrentFloorplanImageUrl(floorId).orElse(null),
                floorId,
                floorName,
                CoordinateType.PIXEL,
                List.of(),
                List.of(),
                List.of(new StepDto("경로를 찾을 수 없습니다.", null, null, target.originalEndX(), target.originalEndY(), null, "INDOOR", null))
        );
    }

    private LegDto createFallbackExitIndoorLeg(RouteTarget target) {
        UUID floorId = target.source() == null ? null : target.source().floorId();
        String floorName = target.source() == null ? null : target.source().floorName();
        String startName = target.source() == null ? target.originalStartName() : target.source().name();

        return new LegDto(
                LegMode.INDOOR,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                startName,
                target.entranceName(),
                MapType.BUILDING,
                floorId == null ? null : mapQueryFacade.findCurrentFloorplanImageUrl(floorId).orElse(null),
                floorId,
                floorName,
                CoordinateType.PIXEL,
                List.of(),
                List.of(),
                List.of(new StepDto("경로를 찾을 수 없습니다.", null, null, target.buildingEntranceX(), target.buildingEntranceY(), null, "INDOOR", null))
        );
    }

    private LegDto createFallbackExitCampusLeg(RouteTarget target) {
        return new LegDto(
                LegMode.CAMPUS,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                target.entranceName(),
                target.campusEntranceName(),
                MapType.CAMPUS,
                target.anchor() == null ? null : mapQueryFacade.findCurrentCampusMapImageUrl(target.anchor().campusId()).orElse(null),
                null,
                null,
                CoordinateType.PIXEL,
                List.of(),
                List.of(),
                List.of(new StepDto("경로를 찾을 수 없습니다.", null, null, target.resolvedOutdoorStartX(), target.resolvedOutdoorStartY(), null, "CAMPUS", null))
        );
    }

    private Optional<ComputedIndoorRoute> computeIndoorRoute(
            RoutingGraph graph,
            UUID startNodeId,
            UUID endNodeId,
            RouteOption routeOption
    ) {
        Map<UUID, RoutingNode> nodes = graph.nodeIndex();
        if (!nodes.containsKey(startNodeId) || !nodes.containsKey(endNodeId)) {
            return Optional.empty();
        }

        Map<UUID, List<RouteLink>> adjacency = buildAdjacency(graph, nodes, routeOption);
        Map<UUID, Double> distance = new HashMap<>();
        Map<UUID, PreviousNode> previous = new HashMap<>();
        PriorityQueue<NodeDistance> queue = new PriorityQueue<>(Comparator.comparingDouble(NodeDistance::distance));
        Set<UUID> visited = new HashSet<>();

        distance.put(startNodeId, 0.0);
        queue.add(new NodeDistance(startNodeId, 0.0));

        while (!queue.isEmpty()) {
            NodeDistance current = queue.poll();
            if (!visited.add(current.nodeId())) {
                continue;
            }
            if (current.nodeId().equals(endNodeId)) {
                break;
            }

            for (RouteLink link : adjacency.getOrDefault(current.nodeId(), List.of())) {
                double nextDistance = current.distance() + link.cost();
                if (nextDistance < distance.getOrDefault(link.toNodeId(), Double.MAX_VALUE)) {
                    distance.put(link.toNodeId(), nextDistance);
                    previous.put(link.toNodeId(), new PreviousNode(current.nodeId(), link));
                    queue.add(new NodeDistance(link.toNodeId(), nextDistance));
                }
            }
        }

        if (!distance.containsKey(endNodeId)) {
            return Optional.empty();
        }

        List<UUID> nodeIds = new ArrayList<>();
        List<RouteLink> links = new ArrayList<>();
        UUID cursor = endNodeId;
        nodeIds.add(cursor);
        while (!cursor.equals(startNodeId)) {
            PreviousNode prev = previous.get(cursor);
            if (prev == null) {
                return Optional.empty();
            }
            links.add(0, prev.link());
            cursor = prev.nodeId();
            nodeIds.add(0, cursor);
        }

        List<RoutingNode> routeNodes = nodeIds.stream().map(nodes::get).toList();
        List<CoordinateDto> path = routeNodes.stream()
                .map(node -> new CoordinateDto(node.x(), node.y(), node.displayName()))
                .toList();
        List<StepDto> rawSteps = buildIndoorSteps(routeNodes, links);
        return Optional.of(new ComputedIndoorRoute(path, mergeIndoorSteps(rawSteps), rawSteps, routeNodes));
    }

    private Map<UUID, List<RouteLink>> buildAdjacency(
            RoutingGraph graph,
            Map<UUID, RoutingNode> nodes,
            RouteOption routeOption
    ) {
        Map<UUID, List<RouteLink>> adjacency = new HashMap<>();

        for (RoutingEdge edge : graph.edges()) {
            RoutingNode from = nodes.get(edge.fromNodeId());
            RoutingNode to = nodes.get(edge.toNodeId());
            if (from == null || to == null) {
                continue;
            }
            double cost = edgeCost(edge, from, to, graph.obstacles(), routeOption);
            if (!Double.isFinite(cost)) {
                continue;
            }
            addLink(adjacency, from.id(), new RouteLink(to.id(), cost, edge.kind(), false, null, null, null));
            if (!edge.directed()) {
                addLink(adjacency, edge.toNodeId(), new RouteLink(from.id(), cost, edge.kind(), false, null, null, null));
            }
        }

        for (VerticalRoutingLink link : graph.verticalLinks()) {
            addLink(adjacency, link.fromNodeId(), new RouteLink(
                    link.toNodeId(),
                    verticalCost(link, routeOption),
                    link.connectorKind(),
                    true,
                    link.connectorKind(),
                    link.displayName(),
                    link.toFloorName()
            ));
        }

        return adjacency;
    }

    private void addLink(Map<UUID, List<RouteLink>> adjacency, UUID fromNodeId, RouteLink link) {
        adjacency.computeIfAbsent(fromNodeId, ignored -> new ArrayList<>()).add(link);
    }

    private double edgeCost(
            RoutingEdge edge,
            RoutingNode from,
            RoutingNode to,
            List<RoutingObstacle> obstacles,
            RouteOption routeOption
    ) {
        double length = edge.length() > 0 ? edge.length() : pixelDistance(from, to);
        double baseWeight = edge.baseWeight() > 0 ? edge.baseWeight() : 1.0;
        double cost = Math.max(length, 1.0) * baseWeight;

        if (isStair(edge.kind()) && routeOption == RouteOption.COMFORTABLE) {
            cost *= COMFORTABLE_STAIR_PENALTY_MULTIPLIER;
        }

        for (RoutingObstacle obstacle : obstacles) {
            if (!obstacle.affectedEdgeIds().contains(edge.id())) {
                continue;
            }
            if (obstacle.blocking()) {
                return BLOCKING_EDGE_COST;
            }
            double extraCost = Math.max(obstacle.extraCost(), 0.0);
            cost += routeOption == RouteOption.COMFORTABLE
                    ? extraCost * COMFORTABLE_OBSTACLE_PENALTY_MULTIPLIER
                    : extraCost;
        }

        return cost;
    }

    private double verticalCost(VerticalRoutingLink link, RouteOption routeOption) {
        double cost = Math.max(link.avgWaitSeconds() == null ? 1 : link.avgWaitSeconds(), 1);
        if (isStair(link.connectorKind()) && routeOption == RouteOption.COMFORTABLE) {
            cost *= COMFORTABLE_STAIR_PENALTY_MULTIPLIER;
        }
        return cost;
    }

    private boolean isStair(String kind) {
        return "stair".equalsIgnoreCase(kind);
    }

    private double pixelDistance(RoutingNode from, RoutingNode to) {
        double dx = from.x() - to.x();
        double dy = from.y() - to.y();
        return Math.sqrt(dx * dx + dy * dy);
    }

    private List<StepDto> buildIndoorSteps(List<RoutingNode> nodes, List<RouteLink> links) {
        if (nodes.isEmpty()) {
            return List.of();
        }

        List<StepDto> steps = new ArrayList<>();
        RoutingNode start = nodes.get(0);
        RoutingNode end = nodes.get(nodes.size() - 1);
        int arrivalStartPathIndex = nodes.size() - 1;
        steps.add(new StepDto(start.displayName() + "에서 출발", null, null, start.x(), start.y(), null, "INDOOR", null, 0, 0));

        for (int i = 0; i < links.size(); i++) {
            RouteLink link = links.get(i);
            RoutingNode current = nodes.get(i);
            RoutingNode next = nodes.get(i + 1);

            String instruction;
            if (link.vertical()) {
                instruction = verticalInstruction(link);
            } else {
                instruction = horizontalInstruction(nodes, i, next);
            }

            if (!link.vertical() && isDestinationLandmarkInstruction(instruction, end)) {
                if (i >= links.size() - 2) {
                    arrivalStartPathIndex = Math.min(arrivalStartPathIndex, i);
                    continue;
                }
                instruction = destinationFreeInstruction(instruction);
            }
            steps.add(new StepDto(instruction, null, null, next.x(), next.y(), null, "INDOOR", null, i, i + 1));
        }

        int endPathIndex = nodes.size() - 1;
        steps.add(new StepDto(end.displayName() + " 도착", null, null, end.x(), end.y(), null, "INDOOR", null, arrivalStartPathIndex, endPathIndex));
        return steps;
    }

    private List<StepDto> mergeIndoorSteps(List<StepDto> steps) {
        if (steps.size() < 2) {
            return steps;
        }

        List<StepDto> merged = new ArrayList<>();
        int index = 0;
        while (index < steps.size()) {
            StepDto current = steps.get(index);
            if (!isMergeableIndoorHorizontalStep(current)) {
                merged.add(current);
                index++;
                continue;
            }

            int endIndex = index;
            while (endIndex + 1 < steps.size()
                    && canMergeIndoorSteps(steps, index, endIndex + 1)) {
                endIndex++;
            }

            if (endIndex == index) {
                merged.add(current);
            } else {
                merged.add(mergeIndoorStepCluster(steps, index, endIndex));
            }
            index = endIndex + 1;
        }

        return merged;
    }

    private boolean canMergeIndoorSteps(List<StepDto> steps, int startIndex, int candidateIndex) {
        StepDto first = steps.get(startIndex);
        StepDto candidate = steps.get(candidateIndex);
        if (!isMergeableIndoorHorizontalStep(candidate)) {
            return false;
        }
        if (sameInstruction(first, candidate)) {
            return true;
        }

        String landmark = instructionLandmarkName(first.instruction());
        String candidateLandmark = instructionLandmarkName(candidate.instruction());
        if (landmark == null || !landmark.equals(candidateLandmark)) {
            return false;
        }
        if (candidateIndex == startIndex + 1 && hasStraightAndTurnInstruction(steps, startIndex, candidateIndex)) {
            return true;
        }
        return clusterDistance(steps, startIndex, candidateIndex) <= INDOOR_INSTRUCTION_CLUSTER_DISTANCE_PX;
    }

    private StepDto mergeIndoorStepCluster(List<StepDto> steps, int startIndex, int endIndex) {
        StepDto first = steps.get(startIndex);
        StepDto last = steps.get(endIndex);
        String instruction = sameInstruction(first, last)
                ? first.instruction()
                : mergedLandmarkInstruction(steps, startIndex, endIndex);

        return new StepDto(
                instruction,
                last.distanceMeters(),
                last.durationSeconds(),
                last.x(),
                last.y(),
                last.turnType(),
                last.mode(),
                last.streetName(),
                first.pathStartIndex(),
                last.pathEndIndex()
        );
    }

    private String mergedLandmarkInstruction(List<StepDto> steps, int startIndex, int endIndex) {
        StepDto first = steps.get(startIndex);
        String landmark = instructionLandmarkName(first.instruction());
        if (landmark == null) {
            return first.instruction();
        }

        Double angle = clusterTurnAngle(steps, startIndex, endIndex);
        if (angle == null || Math.abs(angle) < INDOOR_STRAIGHT_ANGLE_DEGREES) {
            String fallbackDirection = lastTurnDirection(steps, startIndex, endIndex);
            if (hasStraightInstruction(steps, startIndex, endIndex) && fallbackDirection != null && angle == null) {
                return landmark + " 앞까지 직진 후 " + fallbackDirection;
            }
            return landmark + " 앞을 지나 계속 직진";
        }

        String direction = visualTurnDirection(angle);
        if (hasStraightInstruction(steps, startIndex, endIndex)) {
            return landmark + " 앞까지 직진 후 " + direction;
        }
        return landmark + " 앞에서 " + direction;
    }

    private boolean hasStraightInstruction(List<StepDto> steps, int startIndex, int endIndex) {
        for (int index = startIndex; index <= endIndex; index++) {
            if (normalizeInstruction(steps.get(index).instruction()).contains("직진")) {
                return true;
            }
        }
        return false;
    }

    private boolean hasStraightAndTurnInstruction(List<StepDto> steps, int startIndex, int endIndex) {
        return hasStraightInstruction(steps, startIndex, endIndex)
                && lastTurnDirection(steps, startIndex, endIndex) != null;
    }

    private String lastTurnDirection(List<StepDto> steps, int startIndex, int endIndex) {
        for (int index = endIndex; index >= startIndex; index--) {
            String instruction = normalizeInstruction(steps.get(index).instruction());
            if (instruction.contains("좌회전")) {
                return "좌회전";
            }
            if (instruction.contains("우회전")) {
                return "우회전";
            }
        }
        return null;
    }

    private Double clusterTurnAngle(List<StepDto> steps, int startIndex, int endIndex) {
        StepDto entryPrevious = startIndex > 0 ? steps.get(startIndex - 1) : null;
        StepDto entryCurrent = steps.get(startIndex);
        StepDto exitPrevious = endIndex > startIndex ? steps.get(endIndex - 1) : steps.get(startIndex);
        StepDto exitCurrent = steps.get(endIndex);
        return turnAngle(entryPrevious, entryCurrent, exitPrevious, exitCurrent);
    }

    private Double turnAngle(StepDto previous, StepDto current, StepDto next) {
        return turnAngle(previous, current, current, next);
    }

    private Double turnAngle(StepDto entryPrevious, StepDto entryCurrent, StepDto exitPrevious, StepDto exitCurrent) {
        if (!hasPoint(entryPrevious) || !hasPoint(entryCurrent) || !hasPoint(exitPrevious) || !hasPoint(exitCurrent)) {
            return null;
        }

        double firstVectorX = entryCurrent.x() - entryPrevious.x();
        double firstVectorY = entryCurrent.y() - entryPrevious.y();
        double secondVectorX = exitCurrent.x() - exitPrevious.x();
        double secondVectorY = exitCurrent.y() - exitPrevious.y();
        if ((firstVectorX == 0 && firstVectorY == 0) || (secondVectorX == 0 && secondVectorY == 0)) {
            return null;
        }

        double cross = (firstVectorX * secondVectorY) - (firstVectorY * secondVectorX);
        double dot = (firstVectorX * secondVectorX) + (firstVectorY * secondVectorY);
        return Math.toDegrees(Math.atan2(cross, dot));
    }

    private String visualTurnDirection(double angle) {
        // Indoor floorplan coordinates are screen/SVG coordinates where y increases downward.
        // From the previous edge to the next edge, a positive cross product is visually clockwise,
        // which is a right turn on the displayed floorplan.
        return angle > 0 ? "우회전" : "좌회전";
    }

    private double clusterDistance(List<StepDto> steps, int startIndex, int endIndex) {
        double distance = 0.0;
        StepDto previous = startIndex > 0 ? steps.get(startIndex - 1) : steps.get(startIndex);
        for (int i = startIndex; i <= endIndex; i++) {
            StepDto current = steps.get(i);
            if (hasPoint(previous) && hasPoint(current)) {
                double dx = previous.x() - current.x();
                double dy = previous.y() - current.y();
                distance += Math.sqrt(dx * dx + dy * dy);
            }
            previous = current;
        }
        return distance;
    }

    private boolean sameInstruction(StepDto first, StepDto second) {
        return normalizeInstruction(first.instruction()).equals(normalizeInstruction(second.instruction()));
    }

    private boolean isMergeableIndoorHorizontalStep(StepDto step) {
        String instruction = normalizeInstruction(step.instruction());
        if (!"INDOOR".equalsIgnoreCase(step.mode()) || instruction.isBlank()) {
            return false;
        }
        if (instruction.contains("출발") || instruction.contains("도착")) {
            return false;
        }
        if (instruction.contains("엘리베이터")
                || instruction.contains("계단")
                || instruction.contains("에스컬레이터")
                || instruction.contains("경사로")) {
            return false;
        }
        return instruction.contains("좌회전")
                || instruction.contains("우회전")
                || instruction.contains("직진");
    }

    private String instructionLandmarkName(String instruction) {
        String text = normalizeInstruction(instruction);
        int frontIndex = text.indexOf(" 앞");
        if (frontIndex <= 0) {
            return null;
        }

        String landmark = text.substring(0, frontIndex).trim();
        return landmark.isBlank() ? null : landmark;
    }

    private String normalizeInstruction(String instruction) {
        return instruction == null ? "" : instruction.replaceAll("\\s+", " ").trim();
    }

    private boolean hasPoint(StepDto step) {
        return step != null && step.x() != null && step.y() != null;
    }

    private boolean isDestinationLandmarkInstruction(String instruction, RoutingNode destination) {
        String destinationLandmark = instructionLandmark(destination);
        String instructionLandmark = instructionLandmarkName(instruction);
        return destinationLandmark != null && destinationLandmark.equals(instructionLandmark);
    }

    private String destinationFreeInstruction(String instruction) {
        String text = normalizeInstruction(instruction);
        if (text.contains("좌회전")) {
            return "좌회전";
        }
        if (text.contains("우회전")) {
            return "우회전";
        }
        if (text.contains("직진")) {
            return "계속 직진";
        }
        return text;
    }

    private String verticalInstruction(RouteLink link) {
        String destinationFloor = link.toFloorName() == null || link.toFloorName().isBlank()
                ? "다른 층"
                : link.toFloorName();
        return switch (safeLower(link.connectorKind())) {
            case "stair" -> "계단을 이용해 " + destinationFloor + "으로 이동";
            case "ramp" -> "경사로를 따라 " + destinationFloor + "으로 이동";
            case "escalator" -> "에스컬레이터를 타고 " + destinationFloor + "으로 이동";
            default -> "엘리베이터를 타고 " + destinationFloor + "으로 이동";
        };
    }

    private String horizontalInstruction(List<RoutingNode> nodes, int linkIndex, RoutingNode next) {
        if (linkIndex == 0) {
            return straightInstruction(next);
        }

        RoutingNode previous = nodes.get(linkIndex - 1);
        RoutingNode current = nodes.get(linkIndex);
        double cross = ((current.x() - previous.x()) * (next.y() - current.y()))
                - ((current.y() - previous.y()) * (next.x() - current.x()));
        double dot = ((current.x() - previous.x()) * (next.x() - current.x()))
                + ((current.y() - previous.y()) * (next.y() - current.y()));
        double angle = Math.toDegrees(Math.atan2(cross, dot));

        if (Math.abs(angle) < 35) {
            return straightInstruction(next);
        }
        return turnInstruction(current, visualTurnDirection(angle));
    }

    private String straightInstruction(RoutingNode next) {
        String landmark = instructionLandmark(next);
        return landmark == null ? "계속 직진" : landmark + " 앞까지 직진";
    }

    private String turnInstruction(RoutingNode current, String direction) {
        String landmark = instructionLandmark(current);
        return landmark == null ? direction : landmark + " 앞에서 " + direction;
    }

    private String instructionLandmark(RoutingNode node) {
        String displayName = node.displayName();
        if (displayName == null || displayName.isBlank()) {
            return null;
        }
        String normalized = displayName.toLowerCase().replaceAll("\\s+", "");
        if (normalized.equals("통로")
                || normalized.equals("복도")
                || normalized.equals("corridor")
                || normalized.equals("이지점")) {
            return null;
        }
        return displayName;
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase();
    }

    private String resolveTransitRouteName(JsonNode legNode) {
        String route = getNullableText(legNode.path("route"));
        if (route != null) {
            return route;
        }

        JsonNode lane = legNode.path("lane");
        if (!lane.isArray() || lane.isEmpty()) {
            lane = legNode.path("Lane");
        }
        if (lane.isArray() && !lane.isEmpty()) {
            JsonNode firstLane = lane.get(0);
            return firstText(firstLane, "route", "name", "busNo", "subwayCode");
        }

        return null;
    }

    private Integer countTransitStations(JsonNode legNode) {
        JsonNode stationList = transitStationListNode(legNode);
        if (stationList.isArray()) {
            return stationList.size();
        }
        return null;
    }

    private List<TransitStopDto> parseTransitStops(JsonNode legNode) {
        JsonNode stationList = transitStationListNode(legNode);
        if (!stationList.isArray()) {
            return List.of();
        }

        String startName = getNullableText(legNode.path("start").path("name"));
        String endName = getNullableText(legNode.path("end").path("name"));
        List<TransitStopDto> stops = new ArrayList<>();
        Set<String> seenStopKeys = new HashSet<>();

        for (JsonNode stationNode : stationList) {
            String name = firstText(
                    stationNode,
                    "stationName",
                    "stationNm",
                    "stopName",
                    "stopNm",
                    "name",
                    "title"
            );
            String stationId = firstText(stationNode, "stationID", "stationId", "stopId", "stopID", "id");
            String normalizedName = normalizeTransitStopName(name);
            String stopKey = transitStopDedupeKey(stationId, normalizedName);
            if (normalizedName == null
                    || normalizedName.equals(normalizeTransitStopName(startName))
                    || normalizedName.equals(normalizeTransitStopName(endName))
                    || !seenStopKeys.add(stopKey)) {
                continue;
            }

            stops.add(new TransitStopDto(
                    name,
                    stationId,
                    firstNonNull(
                            getNullableDouble(stationNode.path("lon")),
                            firstNonNull(getNullableDouble(stationNode.path("x")), getNullableDouble(stationNode.path("stationX")))
                    ),
                    firstNonNull(
                            getNullableDouble(stationNode.path("lat")),
                            firstNonNull(getNullableDouble(stationNode.path("y")), getNullableDouble(stationNode.path("stationY")))
                    )
            ));
        }

        return stops;
    }

    private String transitStopDedupeKey(String stationId, String normalizedName) {
        if (stationId != null && !stationId.isBlank()) {
            return "id:" + stationId.strip();
        }
        return "name:" + normalizedName;
    }

    private JsonNode transitStationListNode(JsonNode legNode) {
        JsonNode stationList = legNode.path("passStopList").path("stationList");
        if (!stationList.isArray()) {
            stationList = legNode.path("passStopList").path("stations");
        }
        if (!stationList.isArray()) {
            stationList = legNode.path("stationList");
        }
        if (!stationList.isArray()) {
            stationList = legNode.path("stations");
        }
        return stationList;
    }

    private String normalizeTransitStopName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.replaceAll("\\s+", "");
    }

    private LegMode parseTransitLegMode(String mode) {
        if (mode == null) {
            return LegMode.OTHER;
        }

        return switch (mode.toUpperCase()) {
            case "WALK" -> LegMode.WALK;
            case "BUS" -> LegMode.BUS;
            case "SUBWAY" -> LegMode.SUBWAY;
            default -> LegMode.OTHER;
        };
    }

    private String buildTransitInstruction(String mode, String startName, String endName, Integer distance) {
        String distanceText = distance == null ? "" : distance + "m ";
        if ("WALK".equalsIgnoreCase(mode)) {
            return distanceText + "도보 이동";
        }
        if (startName != null && endName != null) {
            return startName + "에서 " + endName + "까지 이동";
        }
        return distanceText + "이동";
    }

    private String formatDuration(Integer totalTimeSeconds, boolean includesIndoor) {
        if (totalTimeSeconds == null) {
            return includesIndoor ? "실내 이동" : null;
        }

        int minutes = (int) Math.ceil(totalTimeSeconds / 60.0);
        String duration = minutes + "분";
        return includesIndoor ? duration + " + 실내 이동" : duration;
    }

    private String firstText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = getNullableText(node.path(fieldName));
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String defaultName(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private CoordinateDto firstLinestringCoordinate(JsonNode linestringNode) {
        String linestring = getNullableText(linestringNode);
        if (linestring == null || linestring.isBlank()) {
            return new CoordinateDto(null, null, null);
        }

        String firstPair = linestring.trim().split("\\s+")[0];
        String[] coordinates = firstPair.split(",");
        if (coordinates.length < 2) {
            return new CoordinateDto(null, null, null);
        }

        try {
            return new CoordinateDto(
                    Double.parseDouble(coordinates[0]),
                    Double.parseDouble(coordinates[1]),
                    null
            );
        } catch (NumberFormatException ignored) {
            return new CoordinateDto(null, null, null);
        }
    }

    private <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    private String getNullableText(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private Integer getNullableInt(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.canConvertToInt()) {
            return node.asInt();
        }
        if (node.isTextual()) {
            try {
                return Integer.parseInt(node.asText());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Double getNullableDouble(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asDouble();
        }
        if (node.isTextual()) {
            try {
                return Double.parseDouble(node.asText());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private record ComputedIndoorRoute(
            List<CoordinateDto> path,
            List<StepDto> steps,
            List<StepDto> rawSteps,
            List<RoutingNode> nodes
    ) {
    }

    private record RouteLink(
            UUID toNodeId,
            double cost,
            String kind,
            boolean vertical,
            String connectorKind,
            String connectorName,
            String toFloorName
    ) {
    }

    private record PreviousNode(
            UUID nodeId,
            RouteLink link
    ) {
    }

    private record NodeDistance(
            UUID nodeId,
            double distance
    ) {
    }

    private record RouteTarget(
            double endX,
            double endY,
            Double outdoorStartX,
            Double outdoorStartY,
            String outdoorStartName,
            double originalEndX,
            double originalEndY,
            String originalStartName,
            String endName,
            boolean includesIndoor,
            boolean startsIndoor,
            String originalEndName,
            IndoorPoiDestination source,
            IndoorPoiDestination destination,
            IndoorDestinationAnchor anchor
    ) {

        private static RouteTarget outdoorOnly(double endX, double endY, String endName) {
            return new RouteTarget(endX, endY, null, null, null, endX, endY, null, endName, false, false, endName, null, null, null);
        }

        private static RouteTarget withIndoor(
                IndoorDestinationAnchor anchor,
                IndoorPoiDestination destination,
                double originalEndX,
                double originalEndY,
                String originalEndName
        ) {
            return new RouteTarget(
                    anchor.outdoorTargetX(),
                    anchor.outdoorTargetY(),
                    null,
                    null,
                    null,
                    originalEndX,
                    originalEndY,
                    null,
                    anchor.outdoorTargetName(),
                    true,
                    false,
                    originalEndName,
                    null,
                    destination,
                    anchor
            );
        }

        private static RouteTarget fromIndoorToOutdoor(
                IndoorDestinationAnchor anchor,
                IndoorPoiDestination source,
                double outdoorEndX,
                double outdoorEndY,
                String outdoorEndName
        ) {
            return new RouteTarget(
                    outdoorEndX,
                    outdoorEndY,
                    anchor.outdoorTargetX(),
                    anchor.outdoorTargetY(),
                    anchor.outdoorTargetName(),
                    outdoorEndX,
                    outdoorEndY,
                    source.name(),
                    outdoorEndName,
                    true,
                    true,
                    outdoorEndName,
                    source,
                    null,
                    anchor
            );
        }

        private static RouteTarget fromIndoorToIndoor(
                IndoorPoiDestination source,
                IndoorPoiDestination destination,
                double originalEndX,
                double originalEndY,
                String originalEndName
        ) {
            return new RouteTarget(
                    originalEndX,
                    originalEndY,
                    null,
                    null,
                    null,
                    originalEndX,
                    originalEndY,
                    source.name(),
                    destination.name(),
                    true,
                    true,
                    originalEndName,
                    source,
                    destination,
                    null
            );
        }

        private boolean isIndoorOnly() {
            return source != null && destination != null && anchor == null;
        }

        private boolean hasCampus() {
            return anchor != null && anchor.hasCampus();
        }

        private boolean hasIndoorDestinationNode() {
            return destination != null && destination.anchorNodeId() != null;
        }

        private String campusEntranceName() {
            return anchor == null ? null : anchor.outdoorTargetName();
        }

        private UUID campusId() {
            if (anchor != null) {
                return anchor.campusId();
            }
            if (destination != null && destination.campusId() != null) {
                return destination.campusId();
            }
            return source == null ? null : source.campusId();
        }

        private String campusName() {
            if (anchor != null) {
                return anchor.campusName();
            }
            if (destination != null && destination.campusName() != null && !destination.campusName().isBlank()) {
                return destination.campusName();
            }
            return source == null ? null : source.campusName();
        }

        private String entranceName() {
            return anchor == null ? null : anchor.entranceName();
        }

        private UUID indoorBuildingId() {
            if (anchor != null) {
                return anchor.buildingId();
            }
            if (destination != null && destination.buildingId() != null) {
                return destination.buildingId();
            }
            return source == null ? null : source.buildingId();
        }

        private String buildingName() {
            if (anchor != null && anchor.buildingName() != null && !anchor.buildingName().isBlank()) {
                return anchor.buildingName();
            }
            if (destination != null && destination.buildingName() != null && !destination.buildingName().isBlank()) {
                return destination.buildingName();
            }
            if (source != null && source.buildingName() != null && !source.buildingName().isBlank()) {
                return source.buildingName();
            }
            return null;
        }

        private double buildingEntranceX() {
            return anchor == null ? endX : anchor.x();
        }

        private double buildingEntranceY() {
            return anchor == null ? endY : anchor.y();
        }

        private double outdoorStartX(NavigationRequestDto request) {
            return outdoorStartX == null ? request.startX() : outdoorStartX;
        }

        private double outdoorStartY(NavigationRequestDto request) {
            return outdoorStartY == null ? request.startY() : outdoorStartY;
        }

        private String outdoorStartName(NavigationRequestDto request) {
            return outdoorStartName == null ? request.startName() : outdoorStartName;
        }

        private double resolvedOutdoorStartX() {
            return outdoorStartX == null ? endX : outdoorStartX;
        }

        private double resolvedOutdoorStartY() {
            return outdoorStartY == null ? endY : outdoorStartY;
        }

        private String resolvedOutdoorStartName() {
            return outdoorStartName == null ? endName : outdoorStartName;
        }
    }
}
