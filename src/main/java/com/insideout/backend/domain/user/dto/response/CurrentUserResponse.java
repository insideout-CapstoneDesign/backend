package com.insideout.backend.domain.user.dto.response;

import com.insideout.backend.domain.user.enums.GlobalRole;
import java.util.UUID;

public record CurrentUserResponse(
	UUID userId,
	String email,
	GlobalRole globalRole
) {
}
