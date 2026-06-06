package com.insideout.backend.domain.map.dto.request;

import java.util.List;

public record MapEditorPoiMappingsSaveRequestDTO(
        List<MapEditorPoiMappingRequestDTO> mappings
) {}
