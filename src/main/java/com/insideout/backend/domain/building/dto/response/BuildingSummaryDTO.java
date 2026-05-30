package com.insideout.backend.domain.building.dto.response;

import com.insideout.backend.domain.building.entity.Building;
import com.insideout.backend.domain.building.entity.Floor;
import com.insideout.backend.domain.building.entity.Floorplan;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;


public record BuildingSummaryDTO(
        UUID id,
        UUID tenantId,
        String name,
        String address,
        UUID campusId,
        String campusName,
        int entranceCount,
        boolean requiresFloorplan,
        String activationStatus,
        OffsetDateTime createdAt,
        List<FloorDTO> floors
) {
    public record FloorDTO(
            UUID id,
            int level,
            String name,
            UUID floorplanId,
            String floorplanImageUrl
    ) {
        public static FloorDTO from(Floor floor, Floorplan floorplan, String imageUrl) {
            return new FloorDTO(
                    floor.getId(),
                    floor.getLevel(),
                    floor.getName(),
                    floorplan != null ? floorplan.getId() : null,
                    imageUrl
            );
        }

        public static FloorDTO from(Floor floor, Floorplan floorplan) {
            return from(floor, floorplan, floorplan != null ? floorplan.getImageUrl() : null);
        }

        public static FloorDTO from(Floor floor) {
            return from(floor, null);
        }
    }

    public static BuildingSummaryDTO from(Building building) {
        return from(building, List.of(), java.util.Map.of(), java.util.Map.of());
    }

    public static BuildingSummaryDTO from(Building building, List<Floor> floors) {
        return from(building, floors, java.util.Map.of(), java.util.Map.of());
    }

    public static BuildingSummaryDTO from(Building building, List<Floor> floors, java.util.Map<UUID, Floorplan> floorplanByFloorId) {
        return from(building, floors, floorplanByFloorId, java.util.Map.of());
    }

    public static BuildingSummaryDTO from(Building building, List<Floor> floors, java.util.Map<UUID, Floorplan> floorplanByFloorId, java.util.Map<UUID, String> presignedUrlByFloorplanId) {
        return new BuildingSummaryDTO(
                building.getId(),
                building.getTenant().getId(),
                building.getName(),
                building.getAddress(),
                building.getCampus() != null ? building.getCampus().getId() : null,
                building.getCampus() != null ? building.getCampus().getName() : null,
                building.getEntranceCount(),
                extractRequiresFloorplan(building),
                extractActivationStatus(building),
                building.getCreatedAt(),
                floors != null ? floors.stream()
                        .map(floor -> {
                            Floorplan fp = floorplanByFloorId.get(floor.getId());
                            String url = fp != null ? presignedUrlByFloorplanId.get(fp.getId()) : null;
                            return FloorDTO.from(floor, fp, url);
                        })
                        .toList() : List.of()
        );
    }

    private static boolean extractRequiresFloorplan(Building building) {
        Object value = building.getMeta() != null ? building.getMeta().get("requiresFloorplan") : null;
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue) {
            return Boolean.parseBoolean(stringValue);
        }
        return false;
    }

    private static String extractActivationStatus(Building building) {
        Object value = building.getMeta() != null ? building.getMeta().get("activationStatus") : null;
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue;
        }
        return "draft";
    }
}

