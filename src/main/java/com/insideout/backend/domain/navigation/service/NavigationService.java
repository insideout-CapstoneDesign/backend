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
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.IndoorInfoDto;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.LegDto;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.LegMode;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.RouteDto;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.RouteFailureDto;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.RouteMode;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.RouteOption;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.StepDto;
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
                target.toIndoorInfo(),
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
            Optional<IndoorDestinationAnchor> exitAnchor = mapQueryFacade.findIndoorDestinationAnchor(
                    source.get().buildingId(),
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
        UUID destinationBuildingId = request.destinationBuildingId() != null
                ? request.destinationBuildingId()
                : destination.map(IndoorPoiDestination::buildingId).orElse(null);

        Optional<IndoorDestinationAnchor> anchor = mapQueryFacade.findIndoorDestinationAnchor(
                destinationBuildingId,
                request.endX(),
                request.endY()
        );

        return anchor
                .map(value -> RouteTarget.withIndoor(
                        value,
                        destination.orElse(null),
                        request.endX(),
                        request.endY(),
                        request.endName()
                ))
                .orElseGet(() -> RouteTarget.outdoorOnly(request.endX(), request.endY(), request.endName()));
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
        headers.set("appKey", tmapApiKey);

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
            List<LegDto> legs = parseTransitLegs(itinerary.path("legs"));
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
            legs.add(new LegDto(
                    mode,
                    resolveTransitRouteName(legNode),
                    getNullableText(legNode.path("type")),
                    getNullableInt(legNode.path("sectionTime")),
                    getNullableInt(legNode.path("distance")),
                    countTransitStations(legNode),
                    getNullableText(legNode.path("start").path("name")),
                    getNullableText(legNode.path("end").path("name")),
                    parseTransitSteps(legNode)
            ));
        }

        return legs;
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
        List<StepDto> steps = parseFeatureSteps(features);
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
                null,
                target.endName(),
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
        if (!target.includesIndoor()) {
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
                        route
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
                        route
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
        return new LegDto(
                mode,
                null,
                null,
                null,
                null,
                null,
                startName,
                endName,
                mapType,
                mapImageUrl,
                floorId,
                floorName,
                CoordinateType.PIXEL,
                route.path(),
                buildFloorSegments(mode, mapType, mapImageUrl, floorId, floorName, route),
                route.steps()
        );
    }

    private List<FloorSegmentDto> buildFloorSegments(
            LegMode mode,
            MapType mapType,
            String fallbackMapImageUrl,
            UUID fallbackFloorId,
            String fallbackFloorName,
            ComputedIndoorRoute route
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
                    route.steps()
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
                    && sameFloor(route.nodes().get(endIndex).floorId(), startNode.floorId())) {
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
                    filterStepsForNodes(route.steps(), segmentNodes)
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

    private List<StepDto> filterStepsForNodes(List<StepDto> steps, List<RoutingNode> nodes) {
        if (steps.isEmpty() || nodes.isEmpty()) {
            return List.of();
        }

        return steps.stream()
                .filter(step -> step.x() != null && step.y() != null)
                .filter(step -> nodes.stream().anyMatch(node -> samePixel(node.x(), step.x()) && samePixel(node.y(), step.y())))
                .toList();
    }

    private boolean sameFloor(UUID first, UUID second) {
        return first == null ? second == null : first.equals(second);
    }

    private boolean samePixel(double first, double second) {
        return Math.abs(first - second) < 0.000001;
    }

    private LegDto createFallbackCampusLeg(RouteTarget target) {
        return new LegDto(
                LegMode.CAMPUS,
                null,
                null,
                null,
                null,
                null,
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
        List<StepDto> steps = buildIndoorSteps(routeNodes, links);
        return Optional.of(new ComputedIndoorRoute(path, steps, routeNodes));
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
        steps.add(new StepDto(start.displayName() + "에서 출발", null, null, start.x(), start.y(), null, "INDOOR", null));

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

            steps.add(new StepDto(instruction, null, null, next.x(), next.y(), null, "INDOOR", null));
        }

        RoutingNode end = nodes.get(nodes.size() - 1);
        steps.add(new StepDto(end.displayName() + " 도착", null, null, end.x(), end.y(), null, "INDOOR", null));
        return steps;
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
            return next.displayName() + " 앞까지 직진";
        }

        RoutingNode previous = nodes.get(linkIndex - 1);
        RoutingNode current = nodes.get(linkIndex);
        double cross = ((current.x() - previous.x()) * (next.y() - current.y()))
                - ((current.y() - previous.y()) * (next.x() - current.x()));
        double dot = ((current.x() - previous.x()) * (next.x() - current.x()))
                + ((current.y() - previous.y()) * (next.y() - current.y()));
        double angle = Math.toDegrees(Math.atan2(cross, dot));

        if (Math.abs(angle) < 35) {
            return next.displayName() + " 앞까지 직진";
        }
        if (angle > 0) {
            return current.displayName() + " 앞에서 좌회전";
        }
        return current.displayName() + " 앞에서 우회전";
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
        JsonNode stationList = legNode.path("passStopList").path("stationList");
        if (!stationList.isArray()) {
            stationList = legNode.path("passStopList").path("stations");
        }
        if (stationList.isArray()) {
            return stationList.size();
        }
        return null;
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

        private IndoorInfoDto toIndoorInfo() {
            if (!includesIndoor) {
                return new IndoorInfoDto(false, null, null, null, null, null, null, null);
            }
            return new IndoorInfoDto(
                    true,
                    anchor.campusId(),
                    anchor.campusName(),
                    anchor.campusEntranceName(),
                    anchor.buildingId(),
                    anchor.buildingName(),
                    anchor.entranceNodeId(),
                    anchor.entranceName()
            );
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

        private String entranceName() {
            return anchor == null ? null : anchor.entranceName();
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
    }
}
