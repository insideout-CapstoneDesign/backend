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
            String floorplanImageUrl,
            boolean analysisCompleted
    ) {
        public static FloorDTO from(Floor floor, Floorplan floorplan, String imageUrl, boolean analysisCompleted) {
            return new FloorDTO(
                    floor.getId(),
                    floor.getLevel(),
                    floor.getName(),
                    floorplan != null ? floorplan.getId() : null,
                    imageUrl,
                    analysisCompleted
            );
        }

        public static FloorDTO from(Floor floor, Floorplan floorplan, boolean analysisCompleted) {
            return from(floor, floorplan, floorplan != null ? floorplan.getImageUrl() : null, analysisCompleted);
        }

        public static FloorDTO from(Floor floor) {
            return from(floor, null, false);
        }
    }

    public static BuildingSummaryDTO from(Building building) {
        return from(building, List.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), false);
    }

    public static BuildingSummaryDTO from(Building building, List<Floor> floors) {
        return from(building, floors, java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), false);
    }

    public static BuildingSummaryDTO from(Building building, List<Floor> floors, java.util.Map<UUID, Floorplan> floorplanByFloorId) {
        return from(building, floors, floorplanByFloorId, java.util.Map.of(), java.util.Map.of(), false);
    }

    public static BuildingSummaryDTO from(Building building, List<Floor> floors, java.util.Map<UUID, Floorplan> floorplanByFloorId, java.util.Map<UUID, String> presignedUrlByFloorplanId) {
        return from(building, floors, floorplanByFloorId, presignedUrlByFloorplanId, java.util.Map.of(), false);
    }

    public static BuildingSummaryDTO from(Building building, List<Floor> floors, java.util.Map<UUID, Floorplan> floorplanByFloorId, java.util.Map<UUID, String> presignedUrlByFloorplanId, java.util.Map<UUID, Boolean> analysisCompletedByFloorplanId) {
        return from(building, floors, floorplanByFloorId, presignedUrlByFloorplanId, analysisCompletedByFloorplanId, false);
    }

    public static BuildingSummaryDTO from(
            Building building,
            List<Floor> floors,
            java.util.Map<UUID, Floorplan> floorplanByFloorId,
            java.util.Map<UUID, String> presignedUrlByFloorplanId,
            java.util.Map<UUID, Boolean> analysisCompletedByFloorplanId,
            boolean published
    ) {
        return new BuildingSummaryDTO(
                building.getId(),
                building.getTenant().getId(),
                building.getName(),
                building.getAddress(),
                building.getCampus() != null ? building.getCampus().getId() : null,
                building.getCampus() != null ? building.getCampus().getName() : null,
                building.getEntranceCount(),
                extractRequiresFloorplan(building),
                extractActivationStatus(building, published),
                building.getCreatedAt(),
                floors != null ? floors.stream()
                        .map(floor -> {
                            Floorplan fp = floorplanByFloorId.get(floor.getId());
                            String url = fp != null ? presignedUrlByFloorplanId.get(fp.getId()) : null;
                            boolean analysisCompleted = fp != null && Boolean.TRUE.equals(analysisCompletedByFloorplanId.get(fp.getId()));
                            return FloorDTO.from(floor, fp, url, analysisCompleted);
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

    private static String extractActivationStatus(Building building, boolean published) {
        // 명시적으로 저장된 activationStatus가 있으면 우선 적용 (inactive로 비활성화된 경우 포함)
        Object value = building.getMeta() != null ? building.getMeta().get("activationStatus") : null;
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue;
        }
        // meta에 status가 없고 publish된 이력이 있으면 active
        if (published) {
            return "active";
        }
        return "draft";
    }
}
