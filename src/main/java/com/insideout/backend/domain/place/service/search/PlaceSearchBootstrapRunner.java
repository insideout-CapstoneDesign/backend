package com.insideout.backend.domain.place.service.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnBean(JdbcTemplate.class)
@RequiredArgsConstructor
public class PlaceSearchBootstrapRunner implements ApplicationRunner {

    private static final long DEFAULT_LOCK_ID = 91_000_001L;

    private final PlaceSearchBackfillService placeSearchBackfillService;
    private final JdbcTemplate jdbcTemplate;

    @Value("${place.search.bootstrap.enabled:false}")
    private boolean bootstrapEnabled;

    @Value("${place.search.bootstrap.batch-size:500}")
    private int batchSize;

    @Value("${place.search.bootstrap.lock-id:" + DEFAULT_LOCK_ID + "}")
    private long lockId;

    @Override
    public void run(ApplicationArguments args) {
        if (!bootstrapEnabled) {
            return;
        }

        Boolean acquired = jdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_lock(?)",
                Boolean.class,
                lockId
        );
        if (!Boolean.TRUE.equals(acquired)) {
            log.info("[PlaceSearchBootstrap] Skip backfill. Lock not acquired. lockId={}", lockId);
            return;
        }

        log.info("[PlaceSearchBootstrap] ES initial backfill started. batchSize={}", batchSize);
        try {
            PlaceSearchBackfillService.BackfillResult result = placeSearchBackfillService.backfillRegisteredPlaces(batchSize);
            log.info(
                    "[PlaceSearchBootstrap] ES initial backfill completed. fetched={}, indexed={}, pages={}, batchSize={}",
                    result.fetchedCount(),
                    result.indexedCount(),
                    result.processedPages(),
                    result.batchSize()
            );
        } finally {
            try {
                jdbcTemplate.queryForObject(
                        "SELECT pg_advisory_unlock(?)",
                        Boolean.class,
                        lockId
                );
            } catch (Exception e) {
                log.warn("[PlaceSearchBootstrap] Failed to release advisory lock. lockId={}", lockId, e);
            }
        }
    }
}
