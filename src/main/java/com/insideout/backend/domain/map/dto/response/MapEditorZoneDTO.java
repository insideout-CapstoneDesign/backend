package com.insideout.backend.domain.map.dto.response;

import com.insideout.backend.domain.map.entity.Zone;

import java.util.Map;
import java.util.UUID;

public record MapEditorZoneDTO(
        UUID id,
        String kind,
        String name,
        Map<String, Object> geomPx,
        Map<String, Object> properties
) {
    public static MapEditorZoneDTO from(Zone zone) {
        return new MapEditorZoneDTO(
                zone.getId(),
                zone.getKind().name(),
                zone.getName(),
                MapGeometryView.toGeoJson(zone.getGeomPx()),
                zone.getProperties()
        );
    }
}
