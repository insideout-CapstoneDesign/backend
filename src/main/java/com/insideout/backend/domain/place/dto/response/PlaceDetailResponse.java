package com.insideout.backend.domain.place.dto.response;

import java.util.List;
import java.util.UUID;

public record PlaceDetailResponse(
        UUID placeId,
        Long poiId,
        String externalApiId,
        String name,
        String address,
        boolean isRegistered,
        boolean hasIndoorMap,
        List<FloorResponse> floors
) {
    public record FloorResponse(
            UUID floorId,
            int level,
            String name,
            List<PoiResponse> pois
    ) {
    }

    public record PoiResponse(
            Long id,
            String name,
            String floor,
            String externalApiId
    ) {
    }
}
