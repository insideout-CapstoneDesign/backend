package com.insideout.backend.domain.building.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BuildingDirectoryBootstrapRunner implements ApplicationRunner {

    private final BuildingDirectorySyncService buildingDirectorySyncService;

    @Override
    public void run(ApplicationArguments args) {
        int syncedCount = buildingDirectorySyncService.syncAllExisting().size();
        log.info("[BuildingDirectoryBootstrap] building_directory sync completed. synced={}", syncedCount);
    }
}
