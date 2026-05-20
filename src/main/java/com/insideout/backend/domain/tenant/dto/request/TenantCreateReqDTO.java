package com.insideout.backend.domain.tenant.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TenantCreateReqDTO(
        @NotBlank(message = "테넌트 이름(displayName)은 필수입니다.")
        String name,
        @Pattern(regexp = "^[a-z0-9-]+$", message = "slug는 영문 소문자, 숫자, 하이픈만 사용할 수 있습니다.")
        String slug
) { }
