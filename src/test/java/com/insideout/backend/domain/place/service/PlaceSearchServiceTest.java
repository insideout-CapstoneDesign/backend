package com.insideout.backend.domain.place.service;

import com.insideout.backend.domain.place.dto.response.PlaceNearestResponse;
import com.insideout.backend.domain.place.exception.PlaceErrorCode;
import com.insideout.backend.domain.place.exception.PlaceException;
import com.insideout.backend.global.apiPayload.code.GeneralErrorCode;
import com.insideout.backend.global.apiPayload.exception.ProjectException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        assertThatThrownBy(() -> placeSearchService.search("   "))
                .isInstanceOf(ProjectException.class)
                .extracting(ex -> ((ProjectException) ex).getErrorCode())
                .isEqualTo(GeneralErrorCode.BAD_REQUEST);
    }

    @Test
    void findNearest_invalidCoordinate_throwsPlaceException() {
        assertThatThrownBy(() -> placeSearchService.findNearest(120.0, 127.0, 30))
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
