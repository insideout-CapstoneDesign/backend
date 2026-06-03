package com.insideout.backend.domain.map.dto.response;

import com.insideout.backend.domain.map.entity.Node;

import java.util.Map;
import java.util.UUID;

public record MapEditorNodeDTO(
        UUID id,
        String kind,
        String name,
        Map<String, Object> geomPx,
        Map<String, Object> properties,
        String source,
        UUID aiDetectionId
) {
    public static MapEditorNodeDTO from(Node node) {
        return new MapEditorNodeDTO(
                node.getId(),
                node.getKindCode(),
                node.getNameKo(),
                MapGeometryView.toGeoJson(node.getGeomPx()),
                node.getProperties(),
                node.getSource(),
                node.getAiDetectionId()
        );
    }
}
