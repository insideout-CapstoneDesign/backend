package com.insideout.backend.domain.place.dto.response;

import org.springframework.util.StringUtils;

import java.util.UUID;

public record PlaceSearchItemResponse(
        String name,
        String address,
        String roadAddress,
        Double lat,
        Double lng,
        boolean isRegistered,
        String externalApiId,
        Double distanceMeters,
        String parentBuildingName,
        String displayName,
        UUID placeId,
        Long poiId
) {

    public PlaceSearchItemResponse {
        if (!StringUtils.hasText(displayName)) {
            displayName = buildDisplayName(parentBuildingName, name);
        } else if (!StringUtils.hasText(parentBuildingName)
                && StringUtils.hasText(name)
                && displayName.trim().equals(name.trim())) {
            displayName = null;
        }
    }

    public PlaceSearchItemResponse(
            String name,
            String address,
            String roadAddress,
            Double lat,
            Double lng,
            boolean isRegistered,
            String externalApiId,
            Double distanceMeters
    ) {
        this(name, address, roadAddress, lat, lng, isRegistered, externalApiId, distanceMeters, null, null, null, null);
    }

    public PlaceSearchItemResponse(
            String name,
            String address,
            String roadAddress,
            Double lat,
            Double lng,
            boolean isRegistered,
            String externalApiId,
            Double distanceMeters,
            String parentBuildingName,
            String displayName
    ) {
        this(name, address, roadAddress, lat, lng, isRegistered, externalApiId, distanceMeters, parentBuildingName, displayName, null, null);
    }

    private static String buildDisplayName(String parentBuildingName, String name) {
        if (!StringUtils.hasText(name) || !StringUtils.hasText(parentBuildingName)) {
            return null;
        }
        String trimmedParent = parentBuildingName.trim();
        String trimmedName = name.trim();
        if (!trimmedParent.equals(trimmedName)) {
            return trimmedParent + " · " + trimmedName;
        }
        return null;
    }
}
