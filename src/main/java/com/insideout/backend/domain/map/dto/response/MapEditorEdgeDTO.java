package com.insideout.backend.domain.map.dto.response;

import com.insideout.backend.domain.map.entity.Edge;

import java.util.Map;
import java.util.UUID;
import java.math.BigDecimal;

public record MapEditorEdgeDTO(
        UUID id,
        UUID fromNodeId,
        UUID toNodeId,
        String kind,
        Map<String, Object> geomPx,
        boolean isDirected,
        BigDecimal baseWeight,
        Map<String, Object> properties,
        String source,
        UUID aiDetectionId
) {
    public static MapEditorEdgeDTO from(Edge edge) {
        return new MapEditorEdgeDTO(
                edge.getId(),
                edge.getFromNode().getId(),
                edge.getToNode().getId(),
                edge.getKindCode(),
                MapGeometryView.toGeoJson(edge.getGeomPx()),
                edge.isDirected(),
                edge.getBaseWeight(),
                edge.getProperties(),
                edge.getSource(),
                edge.getAiDetectionId()
        );
    }
}
