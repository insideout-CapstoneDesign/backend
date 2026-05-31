package com.insideout.backend.domain.place.service.search;

import com.insideout.backend.domain.place.dto.response.PlaceSearchItemResponse;
import com.insideout.backend.domain.place.service.es.PlaceSuggestElasticsearchClient;
import lombok.RequiredArgsConstructor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlaceSearchIndexingService {

    private final TaskExecutor taskExecutor;
    private final PlaceSuggestElasticsearchClient placeSuggestElasticsearchClient;

    public void upsertFromSearchResultsAsync(List<PlaceSearchItemResponse> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        List<PlaceSuggestElasticsearchClient.SuggestDocument> documents = items.stream()
                .filter(this::isIndexable)
                .map(item -> new PlaceSuggestElasticsearchClient.SuggestDocument(
                        item.name(),
                        item.address(),
                        item.roadAddress(),
                        item.externalApiId(),
                        item.lat(),
                        item.lng()
                ))
                .toList();

        if (documents.isEmpty()) {
            return;
        }

        taskExecutor.execute(() -> placeSuggestElasticsearchClient.upsertDocuments(documents));
    }

    private boolean isIndexable(PlaceSearchItemResponse item) {
        return StringUtils.hasText(item.name())
                && item.lat() != null
                && item.lng() != null;
    }
}
