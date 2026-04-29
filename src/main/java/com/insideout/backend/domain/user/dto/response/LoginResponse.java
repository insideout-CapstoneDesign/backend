package com.insideout.backend.domain.user.dto.response;

public record LoginResponse(
	String accessToken,
	String refreshToken
) {
}
