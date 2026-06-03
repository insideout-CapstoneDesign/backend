package com.insideout.backend.domain.map.dto.response;

import com.insideout.backend.domain.map.entity.Poi;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record MapEditorPoiDTO(
        UUID id,
        Long categoryId,
        String name,
        String code,
        Map<String, Object> geomPx,
        Map<String, Object> footprintPx,
        UUID anchorNodeId,
        List<String> tags,
        Map<String, Object> attrs,
        String externalApiId,
        String source,
        UUID aiDetectionId
) {
    public static MapEditorPoiDTO from(Poi poi) {
        return new MapEditorPoiDTO(
                poi.getId(),
                poi.getCategoryId(),
                poi.getName(),
                poi.getCode(),
                MapGeometryView.toGeoJson(poi.getGeomPx()),
                MapGeometryView.toGeoJson(poi.getFootprintPx()),
                poi.getAnchorNodeId(),
                poi.getTags(),
                poi.getAttrs(),
                poi.getExternalApiId(),
                poi.getSource(),
                poi.getAiDetectionId()
        );
    }
}
