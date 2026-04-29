package com.insideout.backend.domain.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
	@Email @NotBlank String email,
	@NotBlank
	@Pattern(
		regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[\\p{Punct}])[A-Za-z\\d\\p{Punct}]{8,15}$",
		message = "비밀번호는 영문, 숫자, 특수문자를 포함한 8~15자여야 합니다."
	)
	String password,
	@NotBlank @Size(max = 10) String displayName
) {
}
