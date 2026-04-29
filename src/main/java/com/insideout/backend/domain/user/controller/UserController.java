package com.insideout.backend.domain.user.controller;

import com.insideout.backend.domain.user.dto.request.LoginRequest;
import com.insideout.backend.domain.user.dto.request.SignupRequest;
import com.insideout.backend.domain.user.dto.response.LoginResponse;
import com.insideout.backend.domain.user.dto.response.SignupResponse;
import com.insideout.backend.domain.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;

	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.CREATED)
	public SignupResponse signup(@Valid @RequestBody SignupRequest request) {
		return userService.signup(request);
	}

	@PostMapping("/login")
	@ResponseStatus(HttpStatus.OK)
	public LoginResponse login(@Valid @RequestBody LoginRequest request) {
		return userService.login(request);
	}

}
