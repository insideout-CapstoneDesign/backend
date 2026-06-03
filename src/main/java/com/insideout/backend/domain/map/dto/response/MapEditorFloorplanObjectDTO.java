package com.insideout.backend.domain.map.dto.response;

import com.insideout.backend.domain.map.entity.FloorplanObject;

import java.util.Map;
import java.util.UUID;

public record MapEditorFloorplanObjectDTO(
        UUID id,
        String kind,
        Map<String, Object> geomPx,
        Map<String, Object> properties,
        String source,
        UUID aiDetectionId
) {
    public static MapEditorFloorplanObjectDTO from(FloorplanObject floorplanObject) {
        return new MapEditorFloorplanObjectDTO(
                floorplanObject.getId(),
                floorplanObject.getKind(),
                MapGeometryView.toGeoJson(floorplanObject.getGeomPx()),
                floorplanObject.getProperties(),
                floorplanObject.getSource(),
                floorplanObject.getAiDetectionId()
        );
    }
}
