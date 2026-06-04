package com.insideout.backend.domain.map.dto.response;

import com.insideout.backend.domain.ai.dto.response.DetectionViewDTO;
import com.insideout.backend.domain.building.entity.Building;
import com.insideout.backend.domain.building.entity.Floor;
import com.insideout.backend.domain.building.entity.Floorplan;
import com.insideout.backend.domain.map.entity.MapVersion;

import java.util.List;
import java.util.UUID;

public record MapEditorInitResponseDTO(
        UUID buildingId,
        String buildingName,
        String buildingAddress,
        String buildingExternalApiId,
        Double buildingLatitude,
        Double buildingLongitude,
        UUID floorId,
        String floorName,
        UUID floorplanId,
        String floorplanImageUrl,
        Integer floorplanWidthPx,
        Integer floorplanHeightPx,
        UUID mapVersionId,
        String mapVersionStatus,
        boolean draftCreated,
        boolean initializedFromAi,
        List<DetectionViewDTO> aiDetections,
        List<MapEditorNodeDTO> nodes,
        List<MapEditorEdgeDTO> edges,
        List<MapEditorPoiDTO> pois,
        List<MapEditorZoneDTO> zones,
        List<MapEditorFloorplanObjectDTO> floorplanObjects
) {
    public static MapEditorInitResponseDTO of(
            Building building,
            Floor floor,
            Floorplan floorplan,
            String floorplanImageUrl,
            MapVersion mapVersion,
            boolean draftCreated,
            boolean initializedFromAi,
            List<DetectionViewDTO> aiDetections,
            List<MapEditorNodeDTO> nodes,
            List<MapEditorEdgeDTO> edges,
            List<MapEditorPoiDTO> pois,
            List<MapEditorZoneDTO> zones,
            List<MapEditorFloorplanObjectDTO> floorplanObjects
    ) {
        return new MapEditorInitResponseDTO(
                building.getId(),
                building.getName(),
                building.getAddress(),
                building.getExternalApiId(),
                resolveBuildingLatitude(building),
                resolveBuildingLongitude(building),
                floor.getId(),
                floor.getName(),
                floorplan != null ? floorplan.getId() : null,
                floorplan != null? floorplanImageUrl : null,
                floorplan != null ? floorplan.getWidthPx() : null,
                floorplan != null ? floorplan.getHeightPx() : null,
                mapVersion.getId(),
                mapVersion.getStatus(),
                draftCreated,
                initializedFromAi,
                aiDetections,
                nodes,
                edges,
                pois,
                zones,
                floorplanObjects
        );
    }

    @SuppressWarnings("unchecked")
    private static Double resolveBuildingLatitude(Building building) {
        Object location = building.getMeta() != null ? building.getMeta().get("location") : null;
        if (location instanceof java.util.Map<?, ?> map) {
            Object latitude = map.get("latitude");
            if (latitude instanceof Number number) {
                return number.doubleValue();
            }
            if (latitude instanceof String value) {
                try {
                    return Double.parseDouble(value);
                } catch (NumberFormatException ignored) {
                    // noop
                }
            }
        }

        if (building.getFootprint() != null) {
            org.locationtech.jts.geom.Point centroid = building.getFootprint().getCentroid();
            return centroid != null ? centroid.getY() : null;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Double resolveBuildingLongitude(Building building) {
        Object location = building.getMeta() != null ? building.getMeta().get("location") : null;
        if (location instanceof java.util.Map<?, ?> map) {
            Object longitude = map.get("longitude");
            if (longitude instanceof Number number) {
                return number.doubleValue();
            }
            if (longitude instanceof String value) {
                try {
                    return Double.parseDouble(value);
                } catch (NumberFormatException ignored) {
                    // noop
                }
            }
        }

        if (building.getFootprint() != null) {
            org.locationtech.jts.geom.Point centroid = building.getFootprint().getCentroid();
            return centroid != null ? centroid.getX() : null;
        }
        return null;
    }
}
