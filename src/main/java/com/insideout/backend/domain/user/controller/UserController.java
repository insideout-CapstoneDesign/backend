package com.insideout.backend.domain.user.controller;

import com.insideout.backend.domain.user.dto.request.LoginRequest;
import com.insideout.backend.domain.user.dto.request.SignupRequest;
import com.insideout.backend.domain.user.dto.response.LoginResponse;
import com.insideout.backend.domain.user.dto.response.SignupResponse;
import com.insideout.backend.domain.user.service.UserService;
import com.insideout.backend.global.apiPayload.ApiResponse;
import com.insideout.backend.global.apiPayload.code.GeneralSuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;

	@PostMapping("/signup")
	public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
		return ApiResponse.success(GeneralSuccessCode.CREATED, userService.signup(request));
	}

	@PostMapping("/login")
	public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		return ApiResponse.success(GeneralSuccessCode.OK, userService.login(request));
	}

}
