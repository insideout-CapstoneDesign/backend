package com.insideout.backend.domain.place.service;

import com.insideout.backend.domain.building.repository.BuildingRepository;
import com.insideout.backend.domain.building.repository.BuildingSearchProjection;
import com.insideout.backend.domain.place.service.es.PlaceSuggestElasticsearchClient;
import com.insideout.backend.domain.place.service.search.PlaceSearchBackfillService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceSearchBackfillServiceTest {

    @Mock
    private BuildingRepository buildingRepository;

    @Mock
    private PlaceSuggestElasticsearchClient placeSuggestElasticsearchClient;

    @InjectMocks
    private PlaceSearchBackfillService placeSearchBackfillService;

    @Test
    void backfillRegisteredPlaces_indexesAllPages() {
        BuildingSearchProjection p1 = projection("건물A", "서울A", 37.5, 127.0, "111");
        BuildingSearchProjection p2 = projection("건물B", "서울B", 37.6, 127.1, "222");
        BuildingSearchProjection p3 = projection("건물C", "서울C", 37.7, 127.2, "333");

        when(buildingRepository.findRegisteredPlacesForIndexing(PageRequest.of(0, 2)))
                .thenReturn(new PageImpl<>(List.of(p1, p2), PageRequest.of(0, 2), 3));
        when(buildingRepository.findRegisteredPlacesForIndexing(PageRequest.of(1, 2)))
                .thenReturn(new PageImpl<>(List.of(p3), PageRequest.of(1, 2), 3));
        when(placeSuggestElasticsearchClient.upsertDocumentsStrict(anyList()))
                .thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());

        PlaceSearchBackfillService.BackfillResult result = placeSearchBackfillService.backfillRegisteredPlaces(2);

        assertThat(result.fetchedCount()).isEqualTo(3);
        assertThat(result.indexedCount()).isEqualTo(3);
        assertThat(result.processedPages()).isEqualTo(2);
        assertThat(result.batchSize()).isEqualTo(2);
        verify(buildingRepository, times(2)).findRegisteredPlacesForIndexing(any());
        verify(placeSuggestElasticsearchClient, times(2)).upsertDocumentsStrict(anyList());
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

