package com.insideout.backend.domain.place.dto.response;

public record PlaceSearchItemResponse(
        String name,
        String address,
        String roadAddress,
        Double lat,
        Double lng,
        boolean isRegistered,
        String externalApiId,
        Double distanceMeters
) {
}
