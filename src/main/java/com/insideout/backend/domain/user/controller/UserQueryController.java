package com.insideout.backend.domain.user.controller;

import com.insideout.backend.domain.user.dto.response.CurrentUserResponse;
import com.insideout.backend.global.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserQueryController {

	@GetMapping("/me")
	public CurrentUserResponse me(@AuthenticationPrincipal CustomUserDetails userDetails) {
		return new CurrentUserResponse(
			userDetails.getUserId(),
			userDetails.getEmail(),
			userDetails.getGlobalRole()
		);
	}
}
