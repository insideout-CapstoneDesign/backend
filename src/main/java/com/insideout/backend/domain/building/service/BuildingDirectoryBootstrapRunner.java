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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

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
        jdbcTemplate.execute((Connection connection) -> {
            try {
                if (!acquireAdvisoryLock(connection)) {
                    log.info("[BuildingDirectoryBootstrap] Skip sync. Lock not acquired. lockId={}", lockId);
                    return null;
                }
            } catch (Exception e) {
                log.error("[BuildingDirectoryBootstrap] Failed to acquire advisory lock. lockId={}", lockId, e);
                return null;
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
                    if (!releaseAdvisoryLock(connection)) {
                        log.warn("[BuildingDirectoryBootstrap] Advisory lock was not released. lockId={}", lockId);
                    }
                } catch (Exception e) {
                    log.warn("[BuildingDirectoryBootstrap] Failed to release advisory lock. lockId={}", lockId, e);
                }
            }
            return null;
        });
    }

    private boolean acquireAdvisoryLock(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            statement.setLong(1, lockId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getBoolean(1);
            }
        }
    }

    private boolean releaseAdvisoryLock(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            statement.setLong(1, lockId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getBoolean(1);
            }
        }
    }
}
