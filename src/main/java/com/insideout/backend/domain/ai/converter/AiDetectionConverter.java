package com.insideout.backend.domain.ai.converter;

import com.insideout.backend.domain.ai.dto.client.AiDetectionDTO;
import com.insideout.backend.domain.ai.entity.AiDetection;
import com.insideout.backend.domain.ai.entity.AiJob;
import com.insideout.backend.domain.ai.entity.CampusAiDetection;
import com.insideout.backend.domain.ai.entity.CampusAiJob;
import com.insideout.backend.domain.ai.exception.AiErrorCode;
import com.insideout.backend.domain.ai.exception.AiException;
import com.insideout.backend.domain.building.entity.CampusMap;
import com.insideout.backend.domain.building.entity.Floorplan;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class AiDetectionConverter {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    public AiDetection toEntity(
            UUID tenantId,
            AiJob job,
            Floorplan floorplan,
            AiDetectionDTO detectionDto
    ) {
        return AiDetection.builder()
                .tenantId(tenantId)
                .job(job)
                .floorplan(floorplan)
                .detectType(detectionDto.detectType())
                .label(detectionDto.label())
                .confidence(BigDecimal.valueOf(detectionDto.confidence()))
                .geomPx(toGeometry(detectionDto.geomPx()))
                .bboxPx(toBox2d(detectionDto.bboxPx()))
                .ocrText(detectionDto.ocrText())
                .attrs(Map.of())
                .status("pending")
                .build();
    }

    public CampusAiDetection toCampusEntity(
            UUID tenantId,
            CampusAiJob job,
            CampusMap campusMap,
            AiDetectionDTO detectionDto
    ) {
        return CampusAiDetection.builder()
                .tenantId(tenantId)
                .job(job)
                .campusMap(campusMap)
                .detectType(detectionDto.detectType())
                .label(detectionDto.label())
                .confidence(BigDecimal.valueOf(detectionDto.confidence()))
                .geomPx(toGeometry(detectionDto.geomPx()))
                .bboxPx(toBox2d(detectionDto.bboxPx()))
                .ocrText(detectionDto.ocrText())
                .attrs(Map.of())
                .status("pending")
                .build();
    }

    private Geometry toGeometry(Map<String, Object> geomPx) {
        if (geomPx == null || geomPx.isEmpty()) {
            throw new AiException(AiErrorCode.AI_INVALID_DETECTION_GEOMETRY);
        }

        Object typeValue = geomPx.get("type");
        Object coordinatesValue = geomPx.get("coordinates");

        if (!(typeValue instanceof String type) || coordinatesValue == null) {
            throw new AiException(AiErrorCode.AI_INVALID_DETECTION_GEOMETRY);
        }

        return switch (type) {
            case "Point" -> toPoint(coordinatesValue);
            case "LineString" -> toLineString(coordinatesValue);
            case "Polygon" -> toPolygon(coordinatesValue);
            default -> throw new AiException(AiErrorCode.AI_INVALID_DETECTION_GEOMETRY);
        };
    }

    private Point toPoint(Object coordinatesValue) {
        Coordinate coordinate = toCoordinate(coordinatesValue);
        return GEOMETRY_FACTORY.createPoint(coordinate);
    }

    private LineString toLineString(Object coordinatesValue) {
        List<?> coordinates = asList(coordinatesValue);
        Coordinate[] lineCoordinates = coordinates.stream()
                .map(this::toCoordinate)
                .toArray(Coordinate[]::new);

        if (lineCoordinates.length < 2) {
            throw new AiException(AiErrorCode.AI_INVALID_DETECTION_GEOMETRY);
        }

        return GEOMETRY_FACTORY.createLineString(lineCoordinates);
    }

    private Polygon toPolygon(Object coordinatesValue) {
        List<?> rings = asList(coordinatesValue);
        if (rings.isEmpty()) {
            throw new AiException(AiErrorCode.AI_INVALID_DETECTION_GEOMETRY);
        }

        Coordinate[] shellCoordinates = requireValidRing(asCoordinateArray(rings.get(0)));

        LinearRing shell = GEOMETRY_FACTORY.createLinearRing(closeRing(shellCoordinates));

        LinearRing[] holes = rings.stream()
                .skip(1)
                .map(this::asCoordinateArray)
                .map(this::requireValidRing)
                .map(this::closeRing)
                .map(GEOMETRY_FACTORY::createLinearRing)
                .toArray(LinearRing[]::new);

        return GEOMETRY_FACTORY.createPolygon(shell, holes);
    }

    private Coordinate[] asCoordinateArray(Object value) {
        List<?> coordinateList = asList(value);
        return coordinateList.stream()
                .map(this::toCoordinate)
                .toArray(Coordinate[]::new);
    }

    private Coordinate[] closeRing(Coordinate[] coordinates) {
        if (coordinates == null || coordinates.length == 0) {
            throw new AiException(AiErrorCode.AI_INVALID_DETECTION_GEOMETRY);
        }

        Coordinate first = coordinates[0];
        Coordinate last = coordinates[coordinates.length - 1];

        if (first.equals2D(last)) {
            return coordinates;
        }

        Coordinate[] closed = new Coordinate[coordinates.length + 1];
        System.arraycopy(coordinates, 0, closed, 0, coordinates.length);
        closed[closed.length - 1] = new Coordinate(first.x, first.y);
        return closed;
    }

    private Coordinate[] requireValidRing(Coordinate[] coordinates) {
        if (coordinates == null || coordinates.length < 4) {
            throw new AiException(AiErrorCode.AI_INVALID_DETECTION_GEOMETRY);
        }
        return coordinates;
    }

    private Coordinate toCoordinate(Object value) {
        List<?> pair = asList(value);
        if (pair.size() < 2 || !(pair.get(0) instanceof Number x) || !(pair.get(1) instanceof Number y)) {
            throw new AiException(AiErrorCode.AI_INVALID_DETECTION_GEOMETRY);
        }
        return new Coordinate(x.doubleValue(), y.doubleValue());
    }

    private List<?> asList(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        throw new AiException(AiErrorCode.AI_INVALID_DETECTION_GEOMETRY);
    }

    private String toBox2d(List<?> bboxPx) {
        if (bboxPx == null || bboxPx.size() < 4) {
            return null;
        }

        try {
            double x = ((Number) bboxPx.get(0)).doubleValue();
            double y = ((Number) bboxPx.get(1)).doubleValue();
            double width = ((Number) bboxPx.get(2)).doubleValue();
            double height = ((Number) bboxPx.get(3)).doubleValue();
            double maxX = x + width;
            double maxY = y + height;

            return String.format("BOX(%s %s,%s %s)", x, y, maxX, maxY);
        } catch (ClassCastException | NullPointerException e) {
            throw new AiException(AiErrorCode.AI_INVALID_DETECTION_GEOMETRY);
        }
    }
}
