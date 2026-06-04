package com.insideout.backend.domain.place.service;

import com.insideout.backend.domain.building.repository.BuildingRepository;
import com.insideout.backend.domain.building.repository.BuildingSearchProjection;
import com.insideout.backend.domain.place.dto.response.PlaceNearestResponse;
import com.insideout.backend.domain.place.dto.response.PlaceSearchItemResponse;
import com.insideout.backend.domain.place.exception.PlaceErrorCode;
import com.insideout.backend.domain.place.exception.PlaceException;
import com.insideout.backend.domain.place.service.es.PlaceSuggestElasticsearchClient;
import com.insideout.backend.domain.place.service.kakao.KakaoPlaceSearchClient;
import com.insideout.backend.domain.place.service.search.PlaceSearchIndexingService;
import com.insideout.backend.domain.place.service.search.PlaceSearchService;
import com.insideout.backend.domain.map.repository.PoiRepository;
import com.insideout.backend.domain.map.repository.RegisteredPoiSearchProjection;
import com.insideout.backend.global.apiPayload.code.GeneralErrorCode;
import com.insideout.backend.global.apiPayload.exception.ProjectException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceSearchServiceTest {

    @Mock
    private KakaoPlaceSearchClient kakaoPlaceSearchClient;

    @Mock
    private BuildingRepository buildingRepository;

    @Mock
    private PoiRepository poiRepository;

    @Mock
    private PlaceSuggestElasticsearchClient placeSuggestElasticsearchClient;

    @Mock
    private PlaceSearchIndexingService placeSearchIndexingService;

    @InjectMocks
    private PlaceSearchService placeSearchService;

    @BeforeEach
    void setUp() {
        lenient().when(buildingRepository.searchRegisteredPlaces(anyString())).thenReturn(List.of());
        lenient().when(buildingRepository.findRegisteredPlacesByExternalApiIds(anyCollection())).thenReturn(List.of());
        lenient().when(buildingRepository.findNearestRegisteredPlace(anyDouble(), anyDouble(), anyInt())).thenReturn(Optional.empty());
        lenient().when(poiRepository.findRegisteredPlacesByExternalApiIds(anyCollection())).thenReturn(List.of());
        lenient().when(placeSuggestElasticsearchClient.search(anyString(), anyInt(), any(), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    void search_blankQuery_throwsBadRequest() {
        assertThatThrownBy(() -> placeSearchService.search("   ", null, null, null, null))
                .isInstanceOf(ProjectException.class)
                .extracting(ex -> ((ProjectException) ex).getErrorCode())
                .isEqualTo(GeneralErrorCode.BAD_REQUEST);
    }

    @Test
    void search_withoutCoordinates_usesKeywordSearch() {
        when(kakaoPlaceSearchClient.searchByKeyword("스타벅스"))
                .thenReturn(List.of());

        placeSearchService.search("스타벅스", null, null, null, null);

        verify(kakaoPlaceSearchClient).searchByKeyword("스타벅스");
        verify(kakaoPlaceSearchClient, never()).searchByKeyword("스타벅스", 37.5, 127.0, 5_000);
    }

    @Test
    void search_withoutCoordinatesButRadius_throwsInvalidCoordinate() {
        assertThatThrownBy(() -> placeSearchService.search("스타벅스", null, null, 3000, null))
                .isInstanceOf(PlaceException.class)
                .extracting(ex -> ((PlaceException) ex).getErrorCode())
                .isEqualTo(PlaceErrorCode.INVALID_COORDINATE);
    }

    @Test
    void search_withCoordinates_usesDistanceSearch() {
        when(kakaoPlaceSearchClient.searchByKeyword("스타벅스", 37.5, 127.0, null))
                .thenReturn(List.of());

        placeSearchService.search("스타벅스", 37.5, 127.0, null, null);

        verify(kakaoPlaceSearchClient).searchByKeyword("스타벅스", 37.5, 127.0, null);
    }

    @Test
    void search_withCoordinatesAndRadius_usesProvidedRadius() {
        when(kakaoPlaceSearchClient.searchByKeyword("스타벅스", 37.5, 127.0, 1500))
                .thenReturn(List.of());

        placeSearchService.search("스타벅스", 37.5, 127.0, 1500, null);

        verify(kakaoPlaceSearchClient).searchByKeyword("스타벅스", 37.5, 127.0, 1500);
    }

    @Test
    void search_withCoordinates_filtersByRadiusAndSortsByDistance() {
        PlaceSearchItemResponse near = new PlaceSearchItemResponse(
                "근처 매장",
                "서울시",
                null,
                37.5005,
                127.0005,
                false,
                "1",
                null
        );
        PlaceSearchItemResponse far = new PlaceSearchItemResponse(
                "먼 매장",
                "제주도",
                null,
                33.4996,
                126.5312,
                false,
                "2",
                null
        );
        when(kakaoPlaceSearchClient.searchByKeyword("스타벅스", 37.5, 127.0, 3000))
                .thenReturn(List.of(far, near));

        List<PlaceSearchItemResponse> result = placeSearchService.search("스타벅스", 37.5, 127.0, 3000, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("근처 매장");
        assertThat(result.get(0).externalApiId()).isEqualTo("1");
        assertThat(result.get(0).distanceMeters()).isNotNull();
    }

    @Test
    void search_mergesRegisteredByExternalApiId_andUsesRegisteredName() {
        when(buildingRepository.searchRegisteredPlaces("신세계"))
                .thenReturn(List.of());
        when(buildingRepository.findRegisteredPlacesByExternalApiIds(anyCollection()))
                .thenReturn(List.of(
                        projection("관리자 등록 건물명", "서울 중구 소공로 63", 37.5609, 126.9810, "7969138")
                ));
        when(kakaoPlaceSearchClient.searchByKeyword("신세계"))
                .thenReturn(List.of(
                        new PlaceSearchItemResponse("외부 건물명", "서울 중구 소공로 63", null, 37.5609, 126.9810, false, "7969138", null)
                ));

        List<PlaceSearchItemResponse> result = placeSearchService.search("신세계", null, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("관리자 등록 건물명");
        assertThat(result.get(0).isRegistered()).isTrue();
    }

    @Test
    void search_marksRegisteredPoiAsRegistered() {
        when(buildingRepository.searchRegisteredPlaces("구찌"))
                .thenReturn(List.of());
        when(buildingRepository.findRegisteredPlacesByExternalApiIds(anyCollection()))
                .thenReturn(List.of());
        when(poiRepository.findRegisteredPlacesByExternalApiIds(anyCollection()))
                .thenReturn(List.of(
                        poiProjection("구찌", "서울 중구 퇴계로 77", "22320326")
                ));
        when(kakaoPlaceSearchClient.searchByKeyword("구찌"))
                .thenReturn(List.of(
                        new PlaceSearchItemResponse("외부 구찌", "서울 중구 퇴계로 77", null, 37.5601, 126.9808, false, "22320326", null)
                ));

        List<PlaceSearchItemResponse> result = placeSearchService.search("구찌", null, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("구찌");
        assertThat(result.get(0).address()).isEqualTo("서울 중구 퇴계로 77");
        assertThat(result.get(0).isRegistered()).isTrue();
    }

    @Test
    void search_withPartialCoordinates_throwsInvalidCoordinate() {
        assertThatThrownBy(() -> placeSearchService.search("스타벅스", 37.5, null, null, null))
                .isInstanceOf(PlaceException.class)
                .extracting(ex -> ((PlaceException) ex).getErrorCode())
                .isEqualTo(PlaceErrorCode.INVALID_COORDINATE);
    }

    @Test
    void search_withInvalidRadius_throwsInvalidRadius() {
        assertThatThrownBy(() -> placeSearchService.search("스타벅스", 37.5, 127.0, 0, null))
                .isInstanceOf(PlaceException.class)
                .extracting(ex -> ((PlaceException) ex).getErrorCode())
                .isEqualTo(PlaceErrorCode.INVALID_RADIUS);
    }

    @Test
    void findNearest_invalidCoordinate_throwsPlaceException() {
        assertThatThrownBy(() -> placeSearchService.findNearest(120.0, 127.0, 30))
                .isInstanceOf(PlaceException.class)
                .extracting(ex -> ((PlaceException) ex).getErrorCode())
                .isEqualTo(PlaceErrorCode.INVALID_COORDINATE);
    }

    @Test
    void findNearest_nanCoordinate_throwsPlaceException() {
        assertThatThrownBy(() -> placeSearchService.findNearest(Double.NaN, 127.0, 30))
                .isInstanceOf(PlaceException.class)
                .extracting(ex -> ((PlaceException) ex).getErrorCode())
                .isEqualTo(PlaceErrorCode.INVALID_COORDINATE);
    }

    @Test
    void findNearest_usesDefaultRadiusWhenMissing() {
        PlaceNearestResponse expected = new PlaceNearestResponse(
                "테스트 건물",
                "서울시 중구 테스트로 1",
                null,
                37.5,
                127.0,
                false,
                null
        );
        when(kakaoPlaceSearchClient.findNearestByCoordinate(37.5, 127.0, 30))
                .thenReturn(Optional.of(expected));

        Optional<PlaceNearestResponse> result = placeSearchService.findNearest(37.5, 127.0, null);

        assertThat(result).contains(expected);
        verify(kakaoPlaceSearchClient).findNearestByCoordinate(37.5, 127.0, 30);
    }

    @Test
    void findNearest_returnsRegisteredBuildingFirst_whenInsideRegisteredFootprint() {
        when(buildingRepository.findNearestRegisteredPlace(37.5, 127.0, 30))
                .thenReturn(Optional.of(projection("등록건물", "서울시", 37.5, 127.0, "111")));

        Optional<PlaceNearestResponse> result = placeSearchService.findNearest(37.5, 127.0, 30);

        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("등록건물");
        assertThat(result.get().isRegistered()).isTrue();
        verify(kakaoPlaceSearchClient, never()).findNearestByCoordinate(37.5, 127.0, 30);
    }

    @Test
    void findNearest_invalidRadius_throwsPlaceException() {
        assertThatThrownBy(() -> placeSearchService.findNearest(37.5, 127.0, 0))
                .isInstanceOf(PlaceException.class)
                .extracting(ex -> ((PlaceException) ex).getErrorCode())
                .isEqualTo(PlaceErrorCode.INVALID_RADIUS);
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

    private RegisteredPoiSearchProjection poiProjection(String name, String address, String externalApiId) {
        return new RegisteredPoiSearchProjection() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getAddress() {
                return address;
            }

            @Override
            public String getExternalApiId() {
                return externalApiId;
            }
        };
    }
}
