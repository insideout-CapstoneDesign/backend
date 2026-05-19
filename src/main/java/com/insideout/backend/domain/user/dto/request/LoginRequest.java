package com.insideout.backend.domain.user.dto.request;

import com.insideout.backend.domain.user.enums.AuthPortalType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LoginRequest(
	@Email @NotBlank String email,
	@NotBlank String password,
	@NotNull AuthPortalType portalType
) {
}
