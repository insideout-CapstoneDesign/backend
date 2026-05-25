package com.insideout.backend.domain.building.dto.response;

import com.insideout.backend.domain.building.dto.CoordinateDTO;
import com.insideout.backend.domain.building.entity.Campus;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CampusResponseDTO(
        UUID id,
        UUID tenantId,
        String name,
        String address,
        List<CoordinateDTO> boundary,
        CoordinateDTO centroid,
        CoordinateDTO primaryEntrance,
        String primaryEntranceName,
        Map<String, Object> meta,
        OffsetDateTime createdAt
) {
    public static CampusResponseDTO from(Campus campus) {
        return new CampusResponseDTO(
                campus.getId(),
                campus.getTenant().getId(),
                campus.getName(),
                campus.getAddress(),
                toBoundaryList(campus.getBoundary()),
                toCoordinateDTO(campus.getCentroid()),
                toCoordinateDTO(campus.getPrimaryEntrance()),
                campus.getPrimaryEntranceName(),
                campus.getMeta(),
                campus.getCreatedAt()
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
}
