package com.insideout.backend.domain.building.dto.response;

import com.insideout.backend.domain.building.entity.CampusMap;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CampusMapResponseDTO(
        UUID id,
        UUID campusId,
        String imageUrl,
        int widthPx,
        int heightPx,
        boolean isCurrent,
        OffsetDateTime uploadedAt
) {
    public static CampusMapResponseDTO from(CampusMap map) {
        return new CampusMapResponseDTO(
                map.getId(),
                map.getCampus().getId(),
                map.getImageUrl(),
                map.getWidthPx(),
                map.getHeightPx(),
                map.isCurrent(),
                map.getUploadedAt()
        );
    }
}
