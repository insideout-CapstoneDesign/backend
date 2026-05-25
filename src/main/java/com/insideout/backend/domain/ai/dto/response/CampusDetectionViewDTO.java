package com.insideout.backend.domain.ai.dto.response;

import com.insideout.backend.domain.ai.entity.CampusAiDetection;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CampusDetectionViewDTO(
        UUID id,
        String detectType,
        String label,
        BigDecimal confidence,
        String ocrText,
        Map<String, Object> geomPx,
        List<Double> bboxPx,
        String status
) {
    public static CampusDetectionViewDTO from(CampusAiDetection detection) {
        return new CampusDetectionViewDTO(
                detection.getId(),
                detection.getDetectType(),
                detection.getLabel(),
                detection.getConfidence(),
                detection.getOcrText(),
                toGeoJson(detection.getGeomPx()),
                toBboxArray(detection.getBboxPx()),
                detection.getStatus()
        );
    }

    private static Map<String, Object> toGeoJson(Geometry geometry) {
        if (geometry == null) {
            return null;
        }

        if (geometry instanceof Point point) {
            return Map.of(
                    "type", "Point",
                    "coordinates", toCoordinate(point.getCoordinate())
            );
        }

        if (geometry instanceof LineString lineString) {
            return Map.of(
                    "type", "LineString",
                    "coordinates", toCoordinates(lineString.getCoordinates())
            );
        }

        if (geometry instanceof Polygon polygon) {
            return Map.of(
                    "type", "Polygon",
                    "coordinates", toPolygonCoordinates(polygon)
            );
        }

        return Map.of(
                "type", geometry.getGeometryType(),
                "wkt", geometry.toText()
        );
    }

    private static List<Double> toBboxArray(String bboxPx) {
        if (bboxPx == null || bboxPx.isBlank()) {
            return null;
        }

        String normalized = bboxPx.trim();
        if (!normalized.startsWith("BOX(") || !normalized.endsWith(")")) {
            return null;
        }

        String content = normalized.substring(4, normalized.length() - 1);
        String[] parts = content.split(",");
        if (parts.length != 2) {
            return null;
        }

        double[] min = parsePoint(parts[0]);
        double[] max = parsePoint(parts[1]);
        if (min == null || max == null) {
            return null;
        }

        double width = max[0] - min[0];
        double height = max[1] - min[1];
        if (width < 0 || height < 0) {
            return null;
        }

        return List.of(
                min[0],
                min[1],
                width,
                height
        );
    }

    private static double[] parsePoint(String value) {
        String[] tokens = value.trim().split("\\s+");
        if (tokens.length != 2) {
            return null;
        }

        try {
            return new double[]{
                    Double.parseDouble(tokens[0]),
                    Double.parseDouble(tokens[1])
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<Double> toCoordinate(Coordinate coordinate) {
        return List.of(coordinate.x, coordinate.y);
    }

    private static List<List<Double>> toCoordinates(Coordinate[] coordinates) {
        return java.util.Arrays.stream(coordinates)
                .map(CampusDetectionViewDTO::toCoordinate)
                .toList();
    }

    private static List<List<List<Double>>> toPolygonCoordinates(Polygon polygon) {
        List<List<List<Double>>> rings = new java.util.ArrayList<>();
        rings.add(toCoordinates(polygon.getExteriorRing().getCoordinates()));

        for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
            rings.add(toCoordinates(polygon.getInteriorRingN(i).getCoordinates()));
        }

        return rings;
    }
}
