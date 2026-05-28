package com.insideout.backend.domain.place.service;

import com.insideout.backend.domain.place.dto.response.PlaceNearestResponse;
import com.insideout.backend.domain.place.dto.response.PlaceSearchItemResponse;
import com.insideout.backend.domain.place.exception.PlaceErrorCode;
import com.insideout.backend.domain.place.exception.PlaceException;
import com.insideout.backend.global.apiPayload.code.GeneralErrorCode;
import com.insideout.backend.global.apiPayload.exception.ProjectException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceSearchServiceTest {

    @Mock
    private KakaoPlaceSearchClient kakaoPlaceSearchClient;

    @InjectMocks
    private PlaceSearchService placeSearchService;

    @Test
    void search_blankQuery_throwsBadRequest() {
        assertThatThrownBy(() -> placeSearchService.search("   ", null, null, null))
                .isInstanceOf(ProjectException.class)
                .extracting(ex -> ((ProjectException) ex).getErrorCode())
                .isEqualTo(GeneralErrorCode.BAD_REQUEST);
    }

    @Test
    void search_withoutCoordinates_usesKeywordSearch() {
        when(kakaoPlaceSearchClient.searchByKeyword("스타벅스"))
                .thenReturn(List.of());

        placeSearchService.search("스타벅스", null, null, null);

        verify(kakaoPlaceSearchClient).searchByKeyword("스타벅스");
        verify(kakaoPlaceSearchClient, never()).searchByKeyword("스타벅스", 37.5, 127.0, 5_000);
    }

    @Test
    void search_withoutCoordinatesButRadius_throwsInvalidCoordinate() {
        assertThatThrownBy(() -> placeSearchService.search("스타벅스", null, null, 3000))
                .isInstanceOf(PlaceException.class)
                .extracting(ex -> ((PlaceException) ex).getErrorCode())
                .isEqualTo(PlaceErrorCode.INVALID_COORDINATE);
    }

    @Test
    void search_withCoordinates_usesDistanceSearch() {
        when(kakaoPlaceSearchClient.searchByKeyword("스타벅스", 37.5, 127.0, 5_000))
                .thenReturn(List.of());

        placeSearchService.search("스타벅스", 37.5, 127.0, null);

        verify(kakaoPlaceSearchClient).searchByKeyword("스타벅스", 37.5, 127.0, 5_000);
    }

    @Test
    void search_withCoordinatesAndRadius_usesProvidedRadius() {
        when(kakaoPlaceSearchClient.searchByKeyword("스타벅스", 37.5, 127.0, 1500))
                .thenReturn(List.of());

        placeSearchService.search("스타벅스", 37.5, 127.0, 1500);

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
                "1"
        );
        PlaceSearchItemResponse far = new PlaceSearchItemResponse(
                "먼 매장",
                "제주도",
                null,
                33.4996,
                126.5312,
                false,
                "2"
        );
        when(kakaoPlaceSearchClient.searchByKeyword("스타벅스", 37.5, 127.0, 3000))
                .thenReturn(List.of(far, near));

        List<PlaceSearchItemResponse> result = placeSearchService.search("스타벅스", 37.5, 127.0, 3000);

        assertThat(result).containsExactly(near);
    }

    @Test
    void search_withPartialCoordinates_throwsInvalidCoordinate() {
        assertThatThrownBy(() -> placeSearchService.search("스타벅스", 37.5, null, null))
                .isInstanceOf(PlaceException.class)
                .extracting(ex -> ((PlaceException) ex).getErrorCode())
                .isEqualTo(PlaceErrorCode.INVALID_COORDINATE);
    }

    @Test
    void search_withInvalidRadius_throwsInvalidRadius() {
        assertThatThrownBy(() -> placeSearchService.search("스타벅스", 37.5, 127.0, 0))
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
    void findNearest_invalidRadius_throwsPlaceException() {
        assertThatThrownBy(() -> placeSearchService.findNearest(37.5, 127.0, 0))
                .isInstanceOf(PlaceException.class)
                .extracting(ex -> ((PlaceException) ex).getErrorCode())
                .isEqualTo(PlaceErrorCode.INVALID_RADIUS);
    }
}
