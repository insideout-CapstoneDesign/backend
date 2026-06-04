package com.insideout.backend.domain.building.dto.response;

import com.insideout.backend.domain.building.dto.CoordinateDTO;
import com.insideout.backend.domain.building.dto.PixelCoordinateDTO;
import com.insideout.backend.domain.map.entity.Node;
import com.insideout.backend.domain.map.entity.Poi;
import org.locationtech.jts.geom.Point;

import java.util.UUID;

public record BuildingEntranceResponseDTO(
        UUID nodeId,
        UUID poiId,
        UUID floorId,
        UUID mapVersionId,
        String name,
        PixelCoordinateDTO pixelCoordinate,
        CoordinateDTO worldCoordinate,
        String campusGateId,
        String campusGateName
) {
    public static BuildingEntranceResponseDTO from(
            Node node,
            Poi poi,
            String campusGateId,
            String campusGateName
    ) {
        return new BuildingEntranceResponseDTO(
                node.getId(),
                poi != null ? poi.getId() : null,
                node.getFloor() != null ? node.getFloor().getId() : null,
                node.getMapVersion() != null ? node.getMapVersion().getId() : null,
                node.getNameKo(),
                toPixelCoordinate(node.getGeomPx()),
                toWorldCoordinate(node.getGeomWgs84()),
                campusGateId,
                campusGateName
        );
    }

    private static PixelCoordinateDTO toPixelCoordinate(Point point) {
        if (point == null) {
            return null;
        }
        return new PixelCoordinateDTO(point.getX(), point.getY());
    }

    private static CoordinateDTO toWorldCoordinate(Point point) {
        if (point == null) {
            return null;
        }
        return new CoordinateDTO(point.getX(), point.getY());
    }
}
