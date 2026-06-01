package com.insideout.backend.domain.user.controller;

import com.insideout.backend.domain.user.dto.response.CurrentUserResponse;
import com.insideout.backend.domain.user.service.UserQueryService;
import com.insideout.backend.global.apiPayload.ApiResponse;
import com.insideout.backend.global.apiPayload.code.GeneralSuccessCode;
import com.insideout.backend.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserQueryController {

	private final UserQueryService userQueryService;

	@GetMapping("/me")
	public ApiResponse<CurrentUserResponse> me(@AuthenticationPrincipal CustomUserDetails userDetails) {
		return ApiResponse.success(GeneralSuccessCode.OK, userQueryService.getCurrentUser(userDetails));
	}
}
