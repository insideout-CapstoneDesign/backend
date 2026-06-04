package com.insideout.backend.domain.map.dto.request;

import java.util.UUID;

public record MapEditorPoiMappingRequestDTO(
        UUID poiId,
        String externalApiId,
        Double latitude,
        Double longitude,
        String placeName,
        String address,
        boolean excluded
) {}
