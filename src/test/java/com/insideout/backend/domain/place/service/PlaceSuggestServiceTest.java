package com.insideout.backend.domain.place.service;

import com.insideout.backend.domain.building.repository.BuildingRepository;
import com.insideout.backend.domain.building.repository.BuildingSearchProjection;
import com.insideout.backend.domain.place.dto.response.PlaceSearchItemResponse;
import com.insideout.backend.domain.place.exception.PlaceErrorCode;
import com.insideout.backend.domain.place.exception.PlaceException;
import com.insideout.backend.domain.place.service.es.PlaceSuggestElasticsearchClient;
import com.insideout.backend.domain.place.service.kakao.KakaoPlaceSearchClient;
import com.insideout.backend.domain.place.service.search.PlaceSearchIndexingService;
import com.insideout.backend.domain.place.service.suggest.PlaceSuggestService;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;

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
        lenient().when(kakaoPlaceSearchClient.searchByKeyword(anyString(), any(), any(), any(), anyInt())).thenReturn(List.of());
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

    @Test
    void suggest_whenElasticsearchHasEnoughResults_skipsKakaoFallback() {
        when(placeSuggestElasticsearchClient.suggest("신세계", 2, null, null))
                .thenReturn(List.of(
                        new PlaceSuggestElasticsearchClient.SuggestDocument(
                                "신세계백화점 본점",
                                "서울 중구",
                                "서울 중구 소공로 63",
                                "1",
                                37.5609,
                                126.9810
                        ),
                        new PlaceSuggestElasticsearchClient.SuggestDocument(
                                "신세계백화점 강남점",
                                "서울 서초구",
                                "서울 서초구 신반포로 176",
                                "2",
                                37.5045,
                                127.0032
                        )
                ));
        when(buildingRepository.findRegisteredPlacesByExternalApiIds(Set.of("1", "2")))
                .thenReturn(List.of());

        List<PlaceSearchItemResponse> result = placeSuggestService.suggest("신세계", null, null, 2);

        assertThat(result).hasSize(2);
        verify(kakaoPlaceSearchClient, never()).searchByKeyword("신세계");
        verify(placeSearchIndexingService, never()).upsertFromSearchResultsAsync(any());
    }

    @Test
    void suggest_whenKakaoFallbackUnavailable_returnsElasticsearchOnly() {
        when(placeSuggestElasticsearchClient.suggest("신세계", 10, null, null))
                .thenReturn(List.of(
                        new PlaceSuggestElasticsearchClient.SuggestDocument(
                                "신세계백화점 본점",
                                "서울 중구",
                                "서울 중구 소공로 63",
                                "1",
                                37.5609,
                                126.9810
                        )
                ));
        lenient().when(kakaoPlaceSearchClient.searchByKeyword("신세계", null, null, null, 10))
                .thenThrow(new PlaceException(PlaceErrorCode.KAKAO_LOCAL_API_UNAVAILABLE));

        List<PlaceSearchItemResponse> result = placeSuggestService.suggest("신세계", null, null, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("신세계백화점 본점");
    }

    @Test
    void suggest_whenElasticsearchUnavailable_usesKakaoFallback() {
        when(placeSuggestElasticsearchClient.suggest("신세계", 10, null, null))
                .thenThrow(new PlaceException(PlaceErrorCode.SEARCH_SERVICE_UNAVAILABLE));
        when(kakaoPlaceSearchClient.searchByKeyword("신세계", null, null, null, 10))
                .thenReturn(List.of(
                        new PlaceSearchItemResponse("신세계백화점 본점", "서울 중구", "서울 중구 소공로 63", 37.5609, 126.9810, false, "1", null)
                ));
        when(buildingRepository.findRegisteredPlacesByExternalApiIds(Set.of("1")))
                .thenReturn(List.of());

        List<PlaceSearchItemResponse> result = placeSuggestService.suggest("신세계", null, null, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("신세계백화점 본점");
        verify(placeSearchIndexingService).upsertFromSearchResultsAsync(any());
    }

    @Test
    void suggest_withCoordinate_evenWhenElasticsearchHasEnoughResults_fetchesNearbyFallback() {
        when(placeSuggestElasticsearchClient.suggest("스타벅스", 10, 37.5609, 126.9810))
                .thenReturn(List.of(
                        new PlaceSuggestElasticsearchClient.SuggestDocument("스타벅스 A", "서울", "서울", "1", 37.6000, 127.0000),
                        new PlaceSuggestElasticsearchClient.SuggestDocument("스타벅스 B", "서울", "서울", "2", 37.6100, 127.0100),
                        new PlaceSuggestElasticsearchClient.SuggestDocument("스타벅스 C", "서울", "서울", "3", 37.6200, 127.0200),
                        new PlaceSuggestElasticsearchClient.SuggestDocument("스타벅스 D", "서울", "서울", "4", 37.6300, 127.0300),
                        new PlaceSuggestElasticsearchClient.SuggestDocument("스타벅스 E", "서울", "서울", "5", 37.6400, 127.0400),
                        new PlaceSuggestElasticsearchClient.SuggestDocument("스타벅스 F", "서울", "서울", "6", 37.6500, 127.0500),
                        new PlaceSuggestElasticsearchClient.SuggestDocument("스타벅스 G", "서울", "서울", "7", 37.6600, 127.0600),
                        new PlaceSuggestElasticsearchClient.SuggestDocument("스타벅스 H", "서울", "서울", "8", 37.6700, 127.0700),
                        new PlaceSuggestElasticsearchClient.SuggestDocument("스타벅스 I", "서울", "서울", "9", 37.6800, 127.0800),
                        new PlaceSuggestElasticsearchClient.SuggestDocument("스타벅스 J", "서울", "서울", "10", 37.6900, 127.0900)
                ));
        when(kakaoPlaceSearchClient.searchByKeyword("스타벅스", 37.5609, 126.9810, null, 10))
                .thenReturn(List.of(
                        new PlaceSearchItemResponse("스타벅스 동국대점", "서울", "서울", 37.5599, 126.9990, false, "11", null)
                ));
        when(buildingRepository.findRegisteredPlacesByExternalApiIds(anySet())).thenReturn(List.of());

        List<PlaceSearchItemResponse> result = placeSuggestService.suggest("스타벅스", 37.5609, 126.9810, 10);

        assertThat(result).isNotEmpty();
        verify(kakaoPlaceSearchClient, times(1))
                .searchByKeyword("스타벅스", 37.5609, 126.9810, null, 10);
    }

    @Test
    void suggest_sortsByKeywordScoreFirst_thenDistance() {
        when(placeSuggestElasticsearchClient.suggest("스타벅스", 10, 37.5609, 126.9810))
                .thenReturn(List.of(
                        new PlaceSuggestElasticsearchClient.SuggestDocument(
                                "스타벅",
                                "서울",
                                "서울",
                                null,
                                37.56091,
                                126.98101
                        ),
                        new PlaceSuggestElasticsearchClient.SuggestDocument(
                                "스타벅스 본점",
                                "서울",
                                "서울",
                                null,
                                37.6000,
                                127.0000
                        )
                ));

        List<PlaceSearchItemResponse> result = placeSuggestService.suggest("스타벅스", 37.5609, 126.9810, 10);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("스타벅스 본점");
    }

    @Test
    void suggest_whenKeywordScoreSame_sortsByDistance() {
        when(placeSuggestElasticsearchClient.suggest("신세계", 10, 37.5609, 126.9810))
                .thenReturn(List.of(
                        new PlaceSuggestElasticsearchClient.SuggestDocument(
                                "신세계백화점 B",
                                "서울",
                                "서울",
                                null,
                                37.5700,
                                126.9900
                        ),
                        new PlaceSuggestElasticsearchClient.SuggestDocument(
                                "신세계백화점 A",
                                "서울",
                                "서울",
                                null,
                                37.56091,
                                126.98101
                        )
                ));

        List<PlaceSearchItemResponse> result = placeSuggestService.suggest("신세계", 37.5609, 126.9810, 10);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("신세계백화점 A");
        assertThat(result.get(0).distanceMeters()).isLessThan(result.get(1).distanceMeters());
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
