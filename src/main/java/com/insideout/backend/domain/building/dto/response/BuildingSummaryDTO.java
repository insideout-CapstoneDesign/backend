package com.insideout.backend.domain.building.dto.response;

import com.insideout.backend.domain.building.entity.Building;
import com.insideout.backend.domain.building.entity.Floor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record BuildingSummaryDTO(
        UUID id,
        String name,
        String address,
        UUID campusId,
        String campusName,
        int entranceCount,
        OffsetDateTime createdAt,
        List<FloorDTO> floors
) {
    public record FloorDTO(
            UUID id,
            int level,
            String name
    ) {
        public static FloorDTO from(Floor floor) {
            return new FloorDTO(floor.getId(), floor.getLevel(), floor.getName());
        }
    }

    public static BuildingSummaryDTO from(Building building) {
        return from(building, List.of());
    }

    public static BuildingSummaryDTO from(Building building, List<Floor> floors) {
        return new BuildingSummaryDTO(
                building.getId(),
                building.getName(),
                building.getAddress(),
                building.getCampus() != null ? building.getCampus().getId() : null,
                building.getCampus() != null ? building.getCampus().getName() : null,
                building.getEntranceCount(),
                building.getCreatedAt(),
                floors != null ? floors.stream().map(FloorDTO::from).toList() : List.of()
        );
    }
}
