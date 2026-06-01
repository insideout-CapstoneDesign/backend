package com.insideout.backend.domain.user.service;

import com.insideout.backend.domain.user.dto.response.CurrentUserResponse;
import com.insideout.backend.global.security.CustomUserDetails;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserQueryService {

	public CurrentUserResponse getCurrentUser(CustomUserDetails userDetails) {
		if (userDetails == null) {
			throw new AuthenticationCredentialsNotFoundException("인증 정보가 없습니다.");
		}
		return CurrentUserResponse.from(userDetails);
	}
}

