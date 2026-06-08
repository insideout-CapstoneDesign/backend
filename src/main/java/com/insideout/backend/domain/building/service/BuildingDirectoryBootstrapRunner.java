package com.insideout.backend.domain.building.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "building.directory.bootstrap.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(JdbcTemplate.class)
@RequiredArgsConstructor
public class BuildingDirectoryBootstrapRunner implements ApplicationRunner {

    private static final long DEFAULT_LOCK_ID = 91_000_002L;

    private final BuildingDirectorySyncService buildingDirectorySyncService;
    private final JdbcTemplate jdbcTemplate;

    @Value("${building.directory.bootstrap.batch-size:500}")
    private int batchSize;

    @Value("${building.directory.bootstrap.lock-id:" + DEFAULT_LOCK_ID + "}")
    private long lockId;

    @Override
    public void run(ApplicationArguments args) {
        Boolean acquired = jdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_lock(?)",
                Boolean.class,
                lockId
        );
        if (!Boolean.TRUE.equals(acquired)) {
            log.info("[BuildingDirectoryBootstrap] Skip sync. Lock not acquired. lockId={}", lockId);
            return;
        }

        try {
            int syncedCount = buildingDirectorySyncService.syncAllExisting(batchSize);
            log.info(
                    "[BuildingDirectoryBootstrap] building_directory sync completed. synced={}, batchSize={}",
                    syncedCount,
                    batchSize
            );
        } catch (Exception e) {
            log.error("[BuildingDirectoryBootstrap] building_directory sync failed. batchSize={}", batchSize, e);
        } finally {
            try {
                jdbcTemplate.queryForObject(
                        "SELECT pg_advisory_unlock(?)",
                        Boolean.class,
                        lockId
                );
            } catch (Exception e) {
                log.warn("[BuildingDirectoryBootstrap] Failed to release advisory lock. lockId={}", lockId, e);
            }
        }
    }
}
