package com.insideout.backend.domain.place.dto.response;

import org.springframework.util.StringUtils;

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
        String displayName
) {

    public PlaceSearchItemResponse {
        if (!StringUtils.hasText(displayName)) {
            displayName = buildDisplayName(parentBuildingName, name);
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
        this(name, address, roadAddress, lat, lng, isRegistered, externalApiId, distanceMeters, null, null);
    }

    private static String buildDisplayName(String parentBuildingName, String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        if (StringUtils.hasText(parentBuildingName)) {
            String trimmedParent = parentBuildingName.trim();
            String trimmedName = name.trim();
            if (!trimmedParent.equals(trimmedName)) {
                return trimmedParent + " · " + trimmedName;
            }
        }
        return name;
    }
}
