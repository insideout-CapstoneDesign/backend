package com.insideout.backend.domain.user.dto.response;

import com.insideout.backend.domain.user.enums.GlobalRole;
import com.insideout.backend.global.security.CustomUserDetails;
import java.util.UUID;

public record CurrentUserResponse(
	UUID userId,
	String email,
	String displayName,
	GlobalRole globalRole
) {
	public static CurrentUserResponse from(CustomUserDetails userDetails) {
		return new CurrentUserResponse(
			userDetails.getUserId(),
			userDetails.getEmail(),
			userDetails.getDisplayName(),
			userDetails.getGlobalRole()
		);
	}
}
