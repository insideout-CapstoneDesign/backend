package com.insideout.backend.domain.place.dto.response;

import java.util.UUID;

public record PlaceNearestResponse(
        String name,
        String address,
        String roadAddress,
        Double lat,
        Double lng,
        boolean isRegistered,
        String externalApiId,
        UUID placeId,
        UUID poiId
) {
    public PlaceNearestResponse(
            String name,
            String address,
            String roadAddress,
            Double lat,
            Double lng,
            boolean isRegistered,
            String externalApiId
    ) {
        this(name, address, roadAddress, lat, lng, isRegistered, externalApiId, null, null);
    }
}
