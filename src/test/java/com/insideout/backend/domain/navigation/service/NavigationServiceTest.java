package com.insideout.backend.domain.navigation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insideout.backend.domain.map.facade.MapQueryFacade;
import com.insideout.backend.domain.map.facade.MapQueryFacade.IndoorDestinationAnchor;
import com.insideout.backend.domain.navigation.dto.NavigationRequestDto;
import com.insideout.backend.domain.navigation.dto.NavigationRequestDto.RouteType;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.LegDto;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.LegMode;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.RouteDto;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto.RouteMode;
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
                .thenReturn(Optional.of(new IndoorDestinationAnchor(
                        buildingId,
                        "테스트 건물",
                        entranceNodeId,
                        "정문",
                        entranceX,
                        entranceY
                )));
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
}
