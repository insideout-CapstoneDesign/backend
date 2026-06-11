package com.insideout.backend.domain.map.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

public record MapEditorVerticalConnectorCreateRequestDTO(
        String kind,
        String name,
        @Min(value = 0, message = "평균 대기 시간은 0 이상이어야 합니다")
        Integer avgWaitSeconds,
        @Pattern(regexp = "^(up|down|both)$", message = "방향은 'up', 'down', 'both' 중 하나여야 합니다")
        String direction
) {}
