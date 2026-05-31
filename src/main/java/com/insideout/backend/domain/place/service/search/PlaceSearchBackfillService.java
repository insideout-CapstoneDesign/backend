package com.insideout.backend.domain.place.service.search;

import com.insideout.backend.domain.building.repository.BuildingRepository;
import com.insideout.backend.domain.building.repository.BuildingSearchProjection;
import com.insideout.backend.domain.place.service.es.PlaceSuggestElasticsearchClient;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlaceSearchBackfillService {

    private static final int DEFAULT_BATCH_SIZE = 500;
    private static final int MAX_BATCH_SIZE = 2000;

    private final BuildingRepository buildingRepository;
    private final PlaceSuggestElasticsearchClient placeSuggestElasticsearchClient;

    public BackfillResult backfillRegisteredPlaces(Integer batchSize) {
        int resolvedBatchSize = resolveBatchSize(batchSize);
        int pageNumber = 0;
        int fetchedCount = 0;
        int indexedCount = 0;
        int processedPages = 0;

        while (true) {
            Page<BuildingSearchProjection> page = buildingRepository
                    .findRegisteredPlacesForIndexing(PageRequest.of(pageNumber, resolvedBatchSize));
            if (page.isEmpty()) {
                break;
            }

            List<PlaceSuggestElasticsearchClient.SuggestDocument> documents = page.getContent().stream()
                    .map(this::toDocument)
                    .filter(this::isIndexable)
                    .toList();

            fetchedCount += page.getNumberOfElements();
            indexedCount += placeSuggestElasticsearchClient.upsertDocumentsStrict(documents);
            processedPages++;

            if (!page.hasNext()) {
                break;
            }
            pageNumber++;
        }

        return new BackfillResult(fetchedCount, indexedCount, processedPages, resolvedBatchSize);
    }

    private int resolveBatchSize(Integer batchSize) {
        if (batchSize == null || batchSize <= 0) {
            return DEFAULT_BATCH_SIZE;
        }
        return Math.min(batchSize, MAX_BATCH_SIZE);
    }

    private PlaceSuggestElasticsearchClient.SuggestDocument toDocument(BuildingSearchProjection building) {
        return new PlaceSuggestElasticsearchClient.SuggestDocument(
                building.getName(),
                building.getAddress(),
                null,
                building.getExternalApiId(),
                building.getLat(),
                building.getLng()
        );
    }

    private boolean isIndexable(PlaceSuggestElasticsearchClient.SuggestDocument document) {
        return StringUtils.hasText(document.name())
                && document.lat() != null
                && document.lng() != null;
    }

    public record BackfillResult(
            int fetchedCount,
            int indexedCount,
            int processedPages,
            int batchSize
    ) {
    }
}

