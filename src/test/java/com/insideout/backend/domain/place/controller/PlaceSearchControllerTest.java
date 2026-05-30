package com.insideout.backend.domain.place.controller;

import com.insideout.backend.domain.place.service.search.PlaceSearchService;
import com.insideout.backend.domain.place.service.suggest.PlaceSuggestService;
import com.insideout.backend.global.apiPayload.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import com.insideout.backend.domain.place.dto.response.PlaceSearchItemResponse;

@ExtendWith(MockitoExtension.class)
class PlaceSearchControllerTest {

    @Mock
    private PlaceSearchService placeSearchService;
    @Mock
    private PlaceSuggestService placeSuggestService;

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
}
