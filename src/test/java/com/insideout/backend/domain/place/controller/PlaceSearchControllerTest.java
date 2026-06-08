package com.insideout.backend.domain.place.controller;

import com.insideout.backend.domain.place.dto.response.PlaceDetailResponse;
import com.insideout.backend.domain.place.dto.response.PlaceDetailResponse.FloorResponse;
import com.insideout.backend.domain.place.dto.response.PlaceDetailResponse.PoiResponse;
import com.insideout.backend.domain.place.service.detail.PlaceDetailService;
import com.insideout.backend.domain.place.service.search.PlaceSearchService;
import com.insideout.backend.domain.place.service.suggest.PlaceSuggestService;
import com.insideout.backend.global.apiPayload.ApiResponse;
import com.insideout.backend.global.apiPayload.exception.ProjectException;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.insideout.backend.domain.place.dto.response.PlaceSearchItemResponse;

@ExtendWith(MockitoExtension.class)
class PlaceSearchControllerTest {

    @Mock
    private PlaceSearchService placeSearchService;
    @Mock
    private PlaceSuggestService placeSuggestService;
    @Mock
    private PlaceDetailService placeDetailService;

    @InjectMocks
    private PlaceSearchController placeSearchController;

    @Test
    void nearest_whenNoResult_returnsPlaceInfoNotAvailableCode() {
        when(placeSearchService.findNearest(37.5, 127.0, 30))
                .thenReturn(Optional.empty());

        ApiResponse<?> response = placeSearchController.nearest(37.5, 127.0, 30);

        assertThat(response.getIsSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("PLACE200_1");
        assertThat(response.getResult()).isNull();
        verify(placeSearchService).findNearest(37.5, 127.0, 30);
    }

    @Test
    void suggest_returnsSuccessResponse() {
        when(placeSuggestService.suggest("스타", 37.5, 127.0, 5))
                .thenReturn(List.of(
                        new PlaceSearchItemResponse("스타벅스", "서울시", "서울시", 37.5, 127.0, false, "1", null)
                ));

        ApiResponse<?> response = placeSearchController.suggest("스타", 37.5, 127.0, 5);

        assertThat(response.getIsSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("COMMON200_1");
        verify(placeSuggestService).suggest("스타", 37.5, 127.0, 5);
    }

    @Test
    void detail_returnsSuccessResponse() {
        String placeId = "7969138";
        PlaceDetailResponse expected = new PlaceDetailResponse(
                java.util.UUID.fromString("2c4a5480-bbf7-4a5d-b3dd-8b7b1e270001"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "18217490",
                "신세계백화점 본점 디 에스테이트",
                "서울특별시 중구 퇴계로 77",
                true,
                true,
                List.of(new FloorResponse(
                        UUID.fromString("11111111-1111-1111-1111-111111111101"),
                        1,
                        "1F",
                        List.of(new PoiResponse(
                                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                                "구찌",
                                "1F",
                                "22320326"
                        ))
                ))
        );
        when(placeDetailService.getDetail(placeId, null)).thenReturn(Optional.of(expected));

        ApiResponse<?> response = placeSearchController.detail(placeId, null);

        assertThat(response.getIsSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("COMMON200_1");
        verify(placeDetailService).getDetail(placeId, null);
    }

    @Test
    void detail_withBlankIdentifiers_throwsBadRequest() {
        assertThatThrownBy(() -> placeSearchController.detail("   ", " "))
                .isInstanceOf(ProjectException.class);
    }
}
