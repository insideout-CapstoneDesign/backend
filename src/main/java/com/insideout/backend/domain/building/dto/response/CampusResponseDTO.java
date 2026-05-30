package com.insideout.backend.domain.building.dto.response;

import com.insideout.backend.domain.building.dto.CoordinateDTO;
import com.insideout.backend.domain.building.dto.CampusGateDTO;
import com.insideout.backend.domain.building.entity.Campus;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.insideout.backend.domain.building.entity.CampusMap;

public record CampusResponseDTO(
        UUID id,
        UUID tenantId,
        String name,
        String address,
        List<CoordinateDTO> boundary,
        CoordinateDTO centroid,
        CoordinateDTO primaryEntrance,
        String primaryEntranceName,
        List<CampusGateDTO> gates,
        boolean requiresFloorplan,
        Map<String, Object> meta,
        OffsetDateTime createdAt,
        UUID currentMapId,
        String currentMapImageUrl
) {
    public static CampusResponseDTO from(Campus campus) {
        return from(campus, null, null);
    }

    public static CampusResponseDTO from(Campus campus, CampusMap currentMap) {
        return from(campus, currentMap, currentMap != null ? currentMap.getImageUrl() : null);
    }

    public static CampusResponseDTO from(Campus campus, CampusMap currentMap, String currentMapImageUrl) {
        return new CampusResponseDTO(
                campus.getId(),
                campus.getTenant().getId(),
                campus.getName(),
                campus.getAddress(),
                toBoundaryList(campus.getBoundary()),
                toCoordinateDTO(campus.getCentroid()),
                toCoordinateDTO(campus.getPrimaryEntrance()),
                campus.getPrimaryEntranceName(),
                extractGates(campus.getMeta(), campus.getPrimaryEntrance(), campus.getPrimaryEntranceName()),
                extractRequiresFloorplan(campus.getMeta()),
                campus.getMeta(),
                campus.getCreatedAt(),
                currentMap != null ? currentMap.getId() : null,
                currentMapImageUrl
        );
    }


    private static CoordinateDTO toCoordinateDTO(Point point) {
        if (point == null) {
            return null;
        }
        return new CoordinateDTO(point.getX(), point.getY());
    }

    private static List<CoordinateDTO> toBoundaryList(Polygon polygon) {
        if (polygon == null) {
            return null;
        }
        List<CoordinateDTO> coords = new ArrayList<>();
        for (org.locationtech.jts.geom.Coordinate c : polygon.getExteriorRing().getCoordinates()) {
            coords.add(new CoordinateDTO(c.x, c.y));
        }
        return coords;
    }

    @SuppressWarnings("unchecked")
    private static List<CampusGateDTO> extractGates(Map<String, Object> meta, Point primaryEntrance, String primaryEntranceName) {
        Object gatesValue = meta != null ? meta.get("gates") : null;
        if (gatesValue instanceof List<?> gates && !gates.isEmpty()) {
            return gates.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .map(entry -> {
                        Object id = entry.get("id");
                        Object name = entry.get("name");
                        Object location = entry.get("location");
                        if (!(location instanceof Map<?, ?> locationMap)) {
                            return null;
                        }

                        Double longitude = asDouble(locationMap.get("longitude"));
                        Double latitude = asDouble(locationMap.get("latitude"));
                        if (name == null || longitude == null || latitude == null) {
                            return null;
                        }

                        return new CampusGateDTO(
                                id != null ? String.valueOf(id) : null,
                                String.valueOf(name),
                                new CoordinateDTO(longitude, latitude)
                        );
                    })
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }

        if (primaryEntrance != null) {
            return List.of(new CampusGateDTO(
                    "primary-gate",
                    primaryEntranceName != null && !primaryEntranceName.isBlank() ? primaryEntranceName : "대표 출입구",
                    new CoordinateDTO(primaryEntrance.getX(), primaryEntrance.getY())
            ));
        }

        return List.of();
    }

    private static boolean extractRequiresFloorplan(Map<String, Object> meta) {
        Object value = meta != null ? meta.get("requiresFloorplan") : null;
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue) {
            return Boolean.parseBoolean(stringValue);
        }
        return false;
    }

    private static Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Double.parseDouble(stringValue);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
