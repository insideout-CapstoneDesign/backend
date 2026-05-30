package com.insideout.backend.domain.navigation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insideout.backend.domain.map.facade.MapQueryFacade;
import com.insideout.backend.domain.map.facade.MapQueryFacade.IndoorDestinationAnchor;
import com.insideout.backend.domain.map.facade.MapQueryFacade.IndoorPoiDestination;
import com.insideout.backend.domain.map.facade.MapQueryFacade.RoutingEdge;
import com.insideout.backend.domain.map.facade.MapQueryFacade.RoutingGraph;
import com.insideout.backend.domain.map.facade.MapQueryFacade.RoutingNode;
import com.insideout.backend.domain.map.facade.MapQueryFacade.VerticalRoutingLink;
import com.insideout.backend.domain.map.enums.MapType;
import com.insideout.backend.domain.navigation.dto.NavigationRequestDto;
import com.insideout.backend.domain.navigation.dto.NavigationRequestDto.RouteType;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.LegDto;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.LegMode;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.RouteDto;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.RouteFailureDto;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.RouteMode;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.RouteOption;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.StepDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class NavigationServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private MapQueryFacade mapQueryFacade;

    @Mock
    private RestTemplate restTemplate;

    private NavigationService navigationService;

    @BeforeEach
    void setUp() {
        navigationService = new NavigationService(mapQueryFacade, restTemplate);
        ReflectionTestUtils.setField(navigationService, "tmapApiKey", "test-tmap-key");
    }

    @Test
    void indoorLegStepUsesOriginalDestinationCoordinates() throws Exception {
        UUID buildingId = UUID.randomUUID();
        UUID entranceNodeId = UUID.randomUUID();
        double originalEndX = 127.1000;
        double originalEndY = 37.5000;
        double entranceX = 127.2000;
        double entranceY = 37.6000;

        when(mapQueryFacade.findIndoorDestinationAnchor(buildingId, originalEndX, originalEndY))
                .thenReturn(Optional.of(indoorAnchor(buildingId, entranceNodeId, entranceX, entranceY)));
        when(restTemplate.postForObject(
                eq("https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1"),
                any(HttpEntity.class),
                eq(JsonNode.class)
        )).thenReturn(walkRouteResponse());

        NavigationResponseDto response = navigationService.findRoutes(new NavigationRequestDto(
                126.9000,
                37.4000,
                originalEndX,
                originalEndY,
                "출발지",
                "실제 목적지",
                buildingId,
                true,
                List.of(RouteType.WALK)
        ));

        RouteDto route = response.routes().get(0);
        LegDto indoorLeg = route.legs().get(route.legs().size() - 1);
        StepDto indoorStep = indoorLeg.steps().get(0);

        assertThat(response.routedDestination().x()).isEqualTo(entranceX);
        assertThat(response.routedDestination().y()).isEqualTo(entranceY);
        assertThat(indoorLeg.mode()).isEqualTo(LegMode.INDOOR);
        assertThat(indoorStep.x()).isEqualTo(originalEndX);
        assertThat(indoorStep.y()).isEqualTo(originalEndY);
        assertThat(route.failures()).isEmpty();
        assertThat(response.failures()).isEmpty();
    }

    @Test
    void routeLookupFailureIsIsolatedByRouteMode() throws Exception {
        when(restTemplate.postForObject(
                eq("https://apis.openapi.sk.com/tmap/routes?version=1&format=json"),
                any(HttpEntity.class),
                eq(JsonNode.class)
        )).thenThrow(new RestClientException("TMAP car failure"));
        when(restTemplate.postForObject(
                eq("https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1"),
                any(HttpEntity.class),
                eq(JsonNode.class)
        )).thenReturn(walkRouteResponse());

        NavigationResponseDto response = navigationService.findRoutes(new NavigationRequestDto(
                126.9000,
                37.4000,
                127.1000,
                37.5000,
                "출발지",
                "목적지",
                null,
                false,
                List.of(RouteType.CAR, RouteType.WALK)
        ));

        assertThat(response.routes())
                .extracting(RouteDto::routeType)
                .containsOnly(RouteMode.WALK);
        assertThat(response.notFoundRouteTypes()).containsExactly(RouteMode.CAR);
        assertThat(response.message()).isEqualTo("일부 이동 수단의 경로를 찾을 수 없습니다.");
    }

    @Test
    void transitHybridFailureUsesTransitCandidateRouteOption() throws Exception {
        UUID buildingId = UUID.randomUUID();
        UUID entranceNodeId = UUID.randomUUID();
        UUID destinationNodeId = UUID.randomUUID();
        UUID floorId = UUID.randomUUID();
        Long destinationPoiId = 1001L;

        when(mapQueryFacade.findIndoorPoiDestination(null)).thenReturn(Optional.empty());
        when(mapQueryFacade.findIndoorPoiDestination(destinationPoiId))
                .thenReturn(Optional.of(indoorPoiDestination(
                        destinationPoiId,
                        destinationNodeId,
                        floorId,
                        "1F",
                        buildingId
                )));
        when(mapQueryFacade.findIndoorDestinationAnchor(buildingId, 127.1000, 37.5000))
                .thenReturn(Optional.of(indoorAnchor(buildingId, entranceNodeId, 127.2000, 37.6000)));
        when(restTemplate.postForObject(
                eq("https://apis.openapi.sk.com/transit/routes"),
                any(HttpEntity.class),
                eq(JsonNode.class)
        )).thenReturn(transitRouteResponse());

        NavigationResponseDto response = navigationService.findRoutes(new NavigationRequestDto(
                126.9000,
                37.4000,
                127.1000,
                37.5000,
                "출발지",
                "목적지",
                buildingId,
                destinationPoiId,
                true,
                List.of(RouteType.TRANSIT)
        ));

        RouteDto route = response.routes().get(0);

        assertThat(route.routeOption()).isEqualTo(RouteOption.TRANSIT_CANDIDATE);
        assertThat(route.failures())
                .extracting(RouteFailureDto::routeOption)
                .containsOnly(RouteOption.TRANSIT_CANDIDATE);
        assertThat(response.failures())
                .extracting(RouteFailureDto::routeOption)
                .containsOnly(RouteOption.TRANSIT_CANDIDATE);
    }

    @Test
    void indoorLegSplitsPathByFloorSegments() throws Exception {
        UUID buildingId = UUID.randomUUID();
        UUID mapVersionId = UUID.randomUUID();
        UUID entranceNodeId = UUID.randomUUID();
        UUID firstFloorConnectorNodeId = UUID.randomUUID();
        UUID secondFloorConnectorNodeId = UUID.randomUUID();
        UUID destinationNodeId = UUID.randomUUID();
        UUID firstFloorId = UUID.randomUUID();
        UUID secondFloorId = UUID.randomUUID();
        Long destinationPoiId = 1001L;

        when(mapQueryFacade.findIndoorPoiDestination(null)).thenReturn(Optional.empty());
        when(mapQueryFacade.findIndoorPoiDestination(destinationPoiId))
                .thenReturn(Optional.of(indoorPoiDestination(
                        destinationPoiId,
                        destinationNodeId,
                        secondFloorId,
                        "2F",
                        buildingId
                )));
        when(mapQueryFacade.findIndoorDestinationAnchor(buildingId, 127.1000, 37.5000))
                .thenReturn(Optional.of(indoorAnchor(buildingId, entranceNodeId, 127.2000, 37.6000)));
        when(mapQueryFacade.findPublishedRoutingGraph(MapType.BUILDING, buildingId))
                .thenReturn(Optional.of(RoutingGraph.builder()
                        .mapVersionId(mapVersionId)
                        .mapType(MapType.BUILDING)
                        .mapImageUrl("https://signed.example/fallback.png")
                        .nodes(List.of(
                                routingNode(entranceNodeId, "entrance", "정문", firstFloorId, "1F", 10, 10),
                                routingNode(firstFloorConnectorNodeId, "elevator", "엘리베이터", firstFloorId, "1F", 20, 20),
                                routingNode(secondFloorConnectorNodeId, "elevator", "엘리베이터", secondFloorId, "2F", 20, 20),
                                routingNode(destinationNodeId, "poi", "목적지 POI", secondFloorId, "2F", 40, 40)
                        ))
                        .edges(List.of(
                                routingEdge(entranceNodeId, firstFloorConnectorNodeId),
                                routingEdge(secondFloorConnectorNodeId, destinationNodeId)
                        ))
                        .verticalLinks(List.of(VerticalRoutingLink.builder()
                                .fromNodeId(firstFloorConnectorNodeId)
                                .toNodeId(secondFloorConnectorNodeId)
                                .connectorKind("elevator")
                                .connectorName("엘리베이터")
                                .fromFloorName("1F")
                                .toFloorName("2F")
                                .avgWaitSeconds(10)
                                .build()))
                        .obstacles(List.of())
                        .build()));
        when(mapQueryFacade.findCurrentFloorplanImageUrl(firstFloorId))
                .thenReturn(Optional.of("https://signed.example/1f.png"));
        when(mapQueryFacade.findCurrentFloorplanImageUrl(secondFloorId))
                .thenReturn(Optional.of("https://signed.example/2f.png"));
        when(restTemplate.postForObject(
                eq("https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1"),
                any(HttpEntity.class),
                eq(JsonNode.class)
        )).thenReturn(walkRouteResponse());

        NavigationResponseDto response = navigationService.findRoutes(new NavigationRequestDto(
                126.9000,
                37.4000,
                127.1000,
                37.5000,
                "출발지",
                "목적지",
                buildingId,
                destinationPoiId,
                true,
                List.of(RouteType.WALK)
        ));

        LegDto indoorLeg = response.routes().get(0).legs().stream()
                .filter(leg -> leg.mode() == LegMode.INDOOR)
                .findFirst()
                .orElseThrow();

        assertThat(indoorLeg.path()).hasSize(4);
        assertThat(indoorLeg.floorSegments()).hasSize(2);
        assertThat(indoorLeg.floorSegments())
                .extracting(segment -> segment.floorId())
                .containsExactly(firstFloorId, secondFloorId);
        assertThat(indoorLeg.floorSegments())
                .extracting(segment -> segment.mapImageUrl())
                .containsExactly("https://signed.example/1f.png", "https://signed.example/2f.png");
        assertThat(indoorLeg.floorSegments().get(0).path()).hasSize(2);
        assertThat(indoorLeg.floorSegments().get(1).path()).hasSize(2);
        assertThat(indoorLeg.floorSegments().get(0).steps())
                .extracting(StepDto::instruction)
                .doesNotContain("엘리베이터를 타고 2F으로 이동");
        assertThat(indoorLeg.floorSegments().get(1).steps())
                .extracting(StepDto::instruction)
                .contains("엘리베이터를 타고 2F으로 이동");
    }

    @Test
    void indoorLegGroupsFloorSegmentsUsingFallbackFloorId() throws Exception {
        UUID buildingId = UUID.randomUUID();
        UUID mapVersionId = UUID.randomUUID();
        UUID entranceNodeId = UUID.randomUUID();
        UUID middleNodeId = UUID.randomUUID();
        UUID destinationNodeId = UUID.randomUUID();
        UUID floorId = UUID.randomUUID();
        Long destinationPoiId = 1001L;

        when(mapQueryFacade.findIndoorPoiDestination(null)).thenReturn(Optional.empty());
        when(mapQueryFacade.findIndoorPoiDestination(destinationPoiId))
                .thenReturn(Optional.of(indoorPoiDestination(
                        destinationPoiId,
                        destinationNodeId,
                        floorId,
                        "1F",
                        buildingId
                )));
        when(mapQueryFacade.findIndoorDestinationAnchor(buildingId, 127.1000, 37.5000))
                .thenReturn(Optional.of(indoorAnchor(buildingId, entranceNodeId, 127.2000, 37.6000)));
        when(mapQueryFacade.findPublishedRoutingGraph(MapType.BUILDING, buildingId))
                .thenReturn(Optional.of(RoutingGraph.builder()
                        .mapVersionId(mapVersionId)
                        .mapType(MapType.BUILDING)
                        .mapImageUrl("https://signed.example/fallback.png")
                        .nodes(List.of(
                                routingNode(entranceNodeId, "entrance", "정문", null, null, 10, 10),
                                routingNode(middleNodeId, "corridor", "복도", floorId, "1F", 20, 20),
                                routingNode(destinationNodeId, "poi", "목적지 POI", null, null, 30, 30)
                        ))
                        .edges(List.of(
                                routingEdge(entranceNodeId, middleNodeId),
                                routingEdge(middleNodeId, destinationNodeId)
                        ))
                        .verticalLinks(List.of())
                        .obstacles(List.of())
                        .build()));
        when(mapQueryFacade.findCurrentFloorplanImageUrl(floorId))
                .thenReturn(Optional.of("https://signed.example/1f.png"));
        when(restTemplate.postForObject(
                eq("https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1"),
                any(HttpEntity.class),
                eq(JsonNode.class)
        )).thenReturn(walkRouteResponse());

        NavigationResponseDto response = navigationService.findRoutes(new NavigationRequestDto(
                126.9000,
                37.4000,
                127.1000,
                37.5000,
                "출발지",
                "목적지",
                buildingId,
                destinationPoiId,
                true,
                List.of(RouteType.WALK)
        ));

        LegDto indoorLeg = response.routes().get(0).legs().stream()
                .filter(leg -> leg.mode() == LegMode.INDOOR)
                .findFirst()
                .orElseThrow();

        assertThat(indoorLeg.floorSegments()).hasSize(1);
        assertThat(indoorLeg.floorSegments().get(0).floorId()).isEqualTo(floorId);
        assertThat(indoorLeg.floorSegments().get(0).path()).hasSize(3);
    }

    private JsonNode walkRouteResponse() throws Exception {
        return OBJECT_MAPPER.readTree("""
                {
                  "features": [
                    {
                      "type": "Feature",
                      "geometry": {
                        "type": "Point",
                        "coordinates": [127.0, 37.0]
                      },
                      "properties": {
                        "description": "도보 이동",
                        "totalTime": 600,
                        "totalDistance": 800,
                        "time": 600,
                        "distance": 800
                      }
                    }
                  ]
                }
                """);
    }

    private IndoorDestinationAnchor indoorAnchor(
            UUID buildingId,
            UUID entranceNodeId,
            double entranceX,
            double entranceY
    ) {
        return IndoorDestinationAnchor.builder()
                .buildingId(buildingId)
                .buildingName("테스트 건물")
                .entranceNodeId(entranceNodeId)
                .entranceName("정문")
                .x(entranceX)
                .y(entranceY)
                .build();
    }

    private IndoorPoiDestination indoorPoiDestination(
            Long publicId,
            UUID anchorNodeId,
            UUID floorId,
            String floorName,
            UUID buildingId
    ) {
        return IndoorPoiDestination.builder()
                .publicId(publicId)
                .poiId(UUID.randomUUID())
                .name("목적지 POI")
                .anchorNodeId(anchorNodeId)
                .floorId(floorId)
                .floorName(floorName)
                .buildingId(buildingId)
                .buildingName("테스트 건물")
                .build();
    }

    private RoutingNode routingNode(
            UUID id,
            String kind,
            String name,
            UUID floorId,
            String floorName,
            double x,
            double y
    ) {
        return RoutingNode.builder()
                .id(id)
                .kind(kind)
                .name(name)
                .floorId(floorId)
                .floorName(floorName)
                .x(x)
                .y(y)
                .build();
    }

    private RoutingEdge routingEdge(UUID fromNodeId, UUID toNodeId) {
        return RoutingEdge.builder()
                .id(UUID.randomUUID())
                .fromNodeId(fromNodeId)
                .toNodeId(toNodeId)
                .kind("corridor")
                .directed(false)
                .length(10)
                .baseWeight(1)
                .build();
    }

    private JsonNode transitRouteResponse() throws Exception {
        return OBJECT_MAPPER.readTree("""
                {
                  "metaData": {
                    "plan": {
                      "itineraries": [
                        {
                          "totalTime": 1200,
                          "totalDistance": 5000,
                          "legs": [
                            {
                              "mode": "WALK",
                              "sectionTime": 300,
                              "distance": 600,
                              "start": {"name": "출발지", "lon": 126.9, "lat": 37.4},
                              "end": {"name": "정류장", "lon": 127.0, "lat": 37.45}
                            }
                          ]
                        }
                      ]
                    }
                  }
                }
                """);
    }
}
