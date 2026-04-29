package com.insideout.backend.domain.user.dto.response;

import com.insideout.backend.domain.user.enums.GlobalRole;
import java.util.UUID;

public record SignupResponse(
	UUID id,
	String email,
	String displayName,
	GlobalRole globalRole
) {
}
