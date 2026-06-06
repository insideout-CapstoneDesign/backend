package com.insideout.backend.domain.map.dto.response;

import java.util.UUID;

public record MapEditorDraftPoiResponseDTO(
        UUID id,
        String name,
        String code,
        String floorName,
        Double pxX,
        Double pxY,
        String externalApiId,
        Double latitude,
        Double longitude,
        String mappedPlaceName,
        String mappedAddress,
        String reviewStatus
) {}
