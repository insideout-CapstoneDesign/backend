package com.insideout.backend.domain.place.controller;

import com.insideout.backend.domain.place.service.PlaceSearchService;
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

@ExtendWith(MockitoExtension.class)
class PlaceSearchControllerTest {

    @Mock
    private PlaceSearchService placeSearchService;

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
}
