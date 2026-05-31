package com.insideout.backend.domain.place.service.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlaceSearchBootstrapRunner implements ApplicationRunner {

    private final PlaceSearchBackfillService placeSearchBackfillService;

    @Value("${place.search.bootstrap.enabled:false}")
    private boolean bootstrapEnabled;

    @Value("${place.search.bootstrap.batch-size:500}")
    private int batchSize;

    @Override
    public void run(ApplicationArguments args) {
        if (!bootstrapEnabled) {
            return;
        }

        log.info("[PlaceSearchBootstrap] ES initial backfill started. batchSize={}", batchSize);
        PlaceSearchBackfillService.BackfillResult result = placeSearchBackfillService.backfillRegisteredPlaces(batchSize);
        log.info(
                "[PlaceSearchBootstrap] ES initial backfill completed. fetched={}, indexed={}, pages={}, batchSize={}",
                result.fetchedCount(),
                result.indexedCount(),
                result.processedPages(),
                result.batchSize()
        );
    }
}

