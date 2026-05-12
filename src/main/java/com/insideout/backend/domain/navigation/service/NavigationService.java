package com.insideout.backend.domain.navigation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.insideout.backend.domain.map.facade.MapQueryFacade;
import com.insideout.backend.domain.map.facade.MapQueryFacade.IndoorDestinationAnchor;
import com.insideout.backend.domain.navigation.dto.NavigationRequestDto;
import com.insideout.backend.domain.navigation.dto.NavigationRequestDto.RouteType;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.CoordinateDto;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.IndoorInfoDto;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.LegDto;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.LegMode;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.RouteDto;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.RouteMode;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.RouteOption;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.StepDto;
import com.insideout.backend.domain.navigation.exception.NavigationErrorCode;
import com.insideout.backend.domain.navigation.exception.NavigationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 실내외 길찾기(Navigation) 기능을 담당하는 핵심 서비스.
 *
 * <p>Repository를 직접 참조하지 않고, MapQueryFacade 등
 * 다른 도메인의 Service를 주입받아 필요한 데이터를 가져옵니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class NavigationService {

    private static final String TMAP_TRANSIT_ROUTES_URL = "https://apis.openapi.sk.com/transit/routes";
    private static final String TMAP_CAR_ROUTES_URL = "https://apis.openapi.sk.com/tmap/routes?version=1&format=json";
    private static final String TMAP_WALK_ROUTES_URL = "https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1";

    private final MapQueryFacade mapQueryFacade;
    private final RestTemplate restTemplate;

    @Value("${tmap.api.key}")
    private String tmapApiKey;

    public NavigationResponseDto findRoutes(NavigationRequestDto request) {
        RouteTarget target = resolveRouteTarget(request);
        EnumSet<RouteType> routeTypes = resolveRouteTypes(request);
        List<RouteDto> routes = new ArrayList<>();
        List<RouteMode> notFoundRouteTypes = new ArrayList<>();

        if (routeTypes.contains(RouteType.TRANSIT)) {
            try {
                List<RouteDto> transitRoutes = findTransitRouteDtos(request, target);
                if (transitRoutes.isEmpty()) {
                    notFoundRouteTypes.add(RouteMode.TRANSIT);
                }
                routes.addAll(transitRoutes);
            } catch (NavigationException e) {
                log.warn("TRANSIT route lookup failed.", e);
                notFoundRouteTypes.add(RouteMode.TRANSIT);
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
            }
        }

        return new NavigationResponseDto(
                new CoordinateDto(request.endX(), request.endY(), request.endName()),
                new CoordinateDto(target.endX(), target.endY(), target.endName()),
                target.toIndoorInfo(),
                routes,
                notFoundRouteTypes,
                resolveRouteMessage(routes, notFoundRouteTypes)
        );
    }

    private String resolveRouteMessage(List<RouteDto> routes, List<RouteMode> notFoundRouteTypes) {
        if (notFoundRouteTypes.isEmpty()) {
            return null;
        }
        if (routes.isEmpty()) {
            return "경로를 찾을 수 없습니다.";
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
                request.destinationBuildingId(),
                request.includeIndoor(),
                List.of(RouteType.TRANSIT)
        );
        return findRoutes(transitOnlyRequest);
    }

    private RouteTarget resolveRouteTarget(NavigationRequestDto request) {
        if (Boolean.FALSE.equals(request.includeIndoor())) {
            return RouteTarget.outdoorOnly(request.endX(), request.endY(), request.endName());
        }

        Optional<IndoorDestinationAnchor> anchor = mapQueryFacade.findIndoorDestinationAnchor(
                request.destinationBuildingId(),
                request.endX(),
                request.endY()
        );

        return anchor
                .map(value -> RouteTarget.withIndoor(value, request.endX(), request.endY(), request.endName()))
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
        body.put("startX", request.startX());
        body.put("startY", request.startY());
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
        body.put("startX", request.startX());
        body.put("startY", request.startY());
        body.put("endX", target.endX());
        body.put("endY", target.endY());
        body.put("reqCoordType", "WGS84GEO");
        body.put("resCoordType", "WGS84GEO");
        body.put("startName", defaultName(request.startName(), "출발"));
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
            addIndoorLegIfNeeded(legs, target);

            routes.add(new RouteDto(
                    RouteMode.TRANSIT,
                    RouteOption.TRANSIT_CANDIDATE,
                    totalTimeSeconds,
                    totalDistanceMeters,
                    formatDuration(totalTimeSeconds, target.includesIndoor()),
                    legs
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
        addIndoorLegIfNeeded(legs, target);

        return Optional.of(new RouteDto(
                routeMode,
                routeOption,
                totalTimeSeconds,
                totalDistanceMeters,
                formatDuration(totalTimeSeconds, target.includesIndoor()),
                legs
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

    private void addIndoorLegIfNeeded(List<LegDto> legs, RouteTarget target) {
        if (!target.includesIndoor()) {
            return;
        }

        legs.add(new LegDto(
                LegMode.INDOOR,
                null,
                null,
                null,
                null,
                null,
                target.entranceName(),
                target.originalEndName(),
                List.of(new StepDto(
                        "실내 이동",
                        null,
                        null,
                        target.originalEndX(),
                        target.originalEndY(),
                        null,
                        "INDOOR",
                        null
                ))
        ));
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

    private Double firstNonNull(Double first, Double second) {
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

    private record RouteTarget(
            double endX,
            double endY,
            double originalEndX,
            double originalEndY,
            String endName,
            boolean includesIndoor,
            String originalEndName,
            IndoorDestinationAnchor anchor
    ) {

        private static RouteTarget outdoorOnly(double endX, double endY, String endName) {
            return new RouteTarget(endX, endY, endX, endY, endName, false, endName, null);
        }

        private static RouteTarget withIndoor(
                IndoorDestinationAnchor anchor,
                double originalEndX,
                double originalEndY,
                String originalEndName
        ) {
            return new RouteTarget(
                    anchor.x(),
                    anchor.y(),
                    originalEndX,
                    originalEndY,
                    defaultAnchorName(anchor),
                    true,
                    originalEndName,
                    anchor
            );
        }

        private IndoorInfoDto toIndoorInfo() {
            if (!includesIndoor) {
                return new IndoorInfoDto(false, null, null, null, null);
            }
            return new IndoorInfoDto(
                    true,
                    anchor.buildingId(),
                    anchor.buildingName(),
                    anchor.entranceNodeId(),
                    anchor.entranceName()
            );
        }

        private String entranceName() {
            return anchor == null ? null : anchor.entranceName();
        }

        private static String defaultAnchorName(IndoorDestinationAnchor anchor) {
            if (anchor.entranceName() != null && !anchor.entranceName().isBlank()) {
                return anchor.entranceName();
            }
            return anchor.buildingName();
        }
    }
}
