package com.insideout.backend.domain.building.dto.response;

import com.insideout.backend.domain.building.entity.Floorplan;
import java.time.OffsetDateTime;
import java.util.UUID;

public record FloorplanResponseDTO(
        UUID id,
        UUID tenantId,
        UUID floorId,
        String imageUrl,
        String imageSha256,
        int widthPx,
        int heightPx,
        UUID uploadedById,
        boolean isCurrent,
        OffsetDateTime uploadedAt
) {
    public static FloorplanResponseDTO from(Floorplan floorplan) {
        return from(floorplan, floorplan.getImageUrl());
    }

    public static FloorplanResponseDTO from(Floorplan floorplan, String imageUrl) {
        return new FloorplanResponseDTO(
                floorplan.getId(),
                floorplan.getTenantId(),
                floorplan.getFloor() != null ? floorplan.getFloor().getId() : null,
                imageUrl,
                floorplan.getImageSha256(),
                floorplan.getWidthPx(),
                floorplan.getHeightPx(),
                floorplan.getUploadedBy() != null ? floorplan.getUploadedBy().getId() : null,
                floorplan.isCurrent(),
                floorplan.getUploadedAt()
        );
    }
}

