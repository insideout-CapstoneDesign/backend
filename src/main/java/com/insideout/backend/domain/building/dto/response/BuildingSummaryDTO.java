package com.insideout.backend.domain.building.dto.response;

import com.insideout.backend.domain.building.entity.Building;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BuildingSummaryDTO(
        UUID id,
        String name,
        String address,
        UUID campusId,
        String campusName,
        int entranceCount,
        OffsetDateTime createdAt
) {
    public static BuildingSummaryDTO from(Building building) {
        return new BuildingSummaryDTO(
                building.getId(),
                building.getName(),
                building.getAddress(),
                building.getCampus() != null ? building.getCampus().getId() : null,
                building.getCampus() != null ? building.getCampus().getName() : null,
                building.getEntranceCount(),
                building.getCreatedAt()
        );
    }
}
