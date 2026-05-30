package com.insideout.backend.domain.place.service;

import com.insideout.backend.domain.building.repository.BuildingRepository;
import com.insideout.backend.domain.building.repository.BuildingSearchProjection;
import com.insideout.backend.domain.place.dto.response.PlaceSearchItemResponse;
import com.insideout.backend.domain.place.exception.PlaceErrorCode;
import com.insideout.backend.domain.place.exception.PlaceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PlaceSuggestServiceTest {

    @Mock
    private PlaceSuggestElasticsearchClient placeSuggestElasticsearchClient;

    @Mock
    private BuildingRepository buildingRepository;

    @Mock
    private KakaoPlaceSearchClient kakaoPlaceSearchClient;

    @Mock
    private PlaceSearchIndexingService placeSearchIndexingService;

    @InjectMocks
    private PlaceSuggestService placeSuggestService;

    @BeforeEach
    void setUp() {
        lenient().when(kakaoPlaceSearchClient.searchByKeyword(anyString())).thenReturn(List.of());
        lenient().when(kakaoPlaceSearchClient.searchByKeyword(anyString(), anyDouble(), anyDouble(), any())).thenReturn(List.of());
    }

    @Test
    void suggest_shortQuery_throwsInvalidQuery() {
        assertThatThrownBy(() -> placeSuggestService.suggest("스", null, null, 10))
                .isInstanceOf(PlaceException.class)
                .extracting(ex -> ((PlaceException) ex).getErrorCode())
                .isEqualTo(PlaceErrorCode.SEARCH_INVALID_QUERY);
    }

    @Test
    void suggest_partialCoordinate_throwsInvalidCoordinate() {
        assertThatThrownBy(() -> placeSuggestService.suggest("스타벅스", 37.5, null, 10))
                .isInstanceOf(PlaceException.class)
                .extracting(ex -> ((PlaceException) ex).getErrorCode())
                .isEqualTo(PlaceErrorCode.INVALID_COORDINATE);
    }

    @Test
    void suggest_externalMatchedBuilding_marksRegisteredTrue() {
        when(placeSuggestElasticsearchClient.suggest("신세계", 10, null, null))
                .thenReturn(List.of(
                        new PlaceSuggestElasticsearchClient.SuggestDocument(
                                "외부 이름",
                                "서울 중구",
                                "서울 중구 소공로",
                                "7969138",
                                37.5609,
                                126.9810
                        )
                ));
        when(buildingRepository.findRegisteredPlacesByExternalApiIds(Set.of("7969138")))
                .thenReturn(List.of(projection("등록 건물명", "서울특별시 중구 소공로 63", 37.56095, 126.98105, "7969138")));

        List<PlaceSearchItemResponse> result = placeSuggestService.suggest("신세계", null, null, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isRegistered()).isTrue();
        assertThat(result.get(0).name()).isEqualTo("등록 건물명");
    }

    @Test
    void suggest_withCoordinate_setsDistanceMeters() {
        when(placeSuggestElasticsearchClient.suggest("신세계", 10, 37.5609, 126.9810))
                .thenReturn(List.of(
                        new PlaceSuggestElasticsearchClient.SuggestDocument(
                                "신세계백화점 본점",
                                "서울 중구",
                                "서울 중구 소공로 63",
                                "111",
                                37.5610,
                                126.9811
                        )
                ));
        when(buildingRepository.findRegisteredPlacesByExternalApiIds(anySet())).thenReturn(List.of());

        List<PlaceSearchItemResponse> result = placeSuggestService.suggest("신세계", 37.5609, 126.9810, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).distanceMeters()).isNotNull();
    }

    @Test
    void suggest_noExternalApiId_skipsRegisteredLookup() {
        when(placeSuggestElasticsearchClient.suggest("신세계", 10, null, null))
                .thenReturn(List.of(
                        new PlaceSuggestElasticsearchClient.SuggestDocument(
                                "신세계백화점 본점",
                                "서울 중구",
                                "서울 중구 소공로 63",
                                null,
                                37.5610,
                                126.9811
                        )
                ));

        placeSuggestService.suggest("신세계", null, null, 10);

        verify(buildingRepository, never()).findRegisteredPlacesByExternalApiIds(anySet());
    }

    private BuildingSearchProjection projection(String name, String address, Double lat, Double lng, String externalApiId) {
        return new BuildingSearchProjection() {
            @Override
            public UUID getId() {
                return UUID.randomUUID();
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getAddress() {
                return address;
            }

            @Override
            public Double getLat() {
                return lat;
            }

            @Override
            public Double getLng() {
                return lng;
            }

            @Override
            public String getExternalApiId() {
                return externalApiId;
            }
        };
    }
}
