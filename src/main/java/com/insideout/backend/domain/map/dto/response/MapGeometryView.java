package com.insideout.backend.domain.map.dto.response;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.util.List;
import java.util.Map;

final class MapGeometryView {

    private MapGeometryView() {
    }

    static Map<String, Object> toGeoJson(Geometry geometry) {
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

    private static List<Double> toCoordinate(Coordinate coordinate) {
        return List.of(coordinate.x, coordinate.y);
    }

    private static List<List<Double>> toCoordinates(Coordinate[] coordinates) {
        return java.util.Arrays.stream(coordinates)
                .map(MapGeometryView::toCoordinate)
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
