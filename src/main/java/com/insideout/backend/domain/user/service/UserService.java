package com.insideout.backend.domain.user.service;

import com.insideout.backend.domain.user.dto.request.LoginRequest;
import com.insideout.backend.domain.user.dto.request.SignupRequest;
import com.insideout.backend.domain.user.dto.response.LoginResponse;
import com.insideout.backend.domain.user.dto.response.SignupResponse;
import com.insideout.backend.domain.user.entity.User;
import com.insideout.backend.domain.user.enums.AuthPortalType;
import com.insideout.backend.domain.user.enums.GlobalRole;
import com.insideout.backend.domain.user.exception.UserErrorCode;
import com.insideout.backend.domain.user.exception.UserException;
import com.insideout.backend.domain.user.repository.UserRepository;
import com.insideout.backend.global.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;

	@Transactional
	public SignupResponse signupUser(SignupRequest request) {
		return signup(request, GlobalRole.END_USER);
	}

	@Transactional
	public SignupResponse signupTenant(SignupRequest request) {
		return signup(request, GlobalRole.TENANT_USER);
	}

	@Transactional(readOnly = true)
	public LoginResponse login(LoginRequest request) {
		User user = userRepository.findByEmail(request.email())
			.orElseThrow(() -> new UserException(UserErrorCode.LOGIN_FAILED));

		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new UserException(UserErrorCode.LOGIN_FAILED);
		}

		validatePortalRole(request.portalType(), user.getGlobalRole());

		String accessToken = jwtTokenProvider.generateAccessToken(user.getEmail());
		String refreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail());

		return new LoginResponse(accessToken, refreshToken);
	}

	private SignupResponse signup(SignupRequest request, GlobalRole role) {
		if (userRepository.existsByEmail(request.email())) {
			throw new UserException(UserErrorCode.EMAIL_ALREADY_EXISTS);
		}
		if (userRepository.existsByDisplayName(request.displayName())) {
			throw new UserException(UserErrorCode.DISPLAY_NAME_ALREADY_EXISTS);
		}

		User user = User.builder()
			.email(request.email())
			.passwordHash(passwordEncoder.encode(request.password()))
			.displayName(request.displayName())
			.globalRole(role)
			.build();

		User saved;
		try {
			saved = userRepository.saveAndFlush(user);
		} catch (DataIntegrityViolationException e) {
			// 선조회 이후 동시 요청 경합으로 unique 제약 위반이 날 수 있어 DB 예외를 도메인 예외로 변환한다.
			if (userRepository.existsByEmail(request.email())) {
				throw new UserException(UserErrorCode.EMAIL_ALREADY_EXISTS);
			}
			if (userRepository.existsByDisplayName(request.displayName())) {
				throw new UserException(UserErrorCode.DISPLAY_NAME_ALREADY_EXISTS);
			}
			throw e;
		}

		return new SignupResponse(
			saved.getId(),
			saved.getEmail(),
			saved.getDisplayName(),
			saved.getGlobalRole()
		);
	}

	private void validatePortalRole(AuthPortalType portalType, GlobalRole role) {
		boolean allowed = switch (portalType) {
			case USER -> role == GlobalRole.END_USER;
			case TENANT -> role == GlobalRole.TENANT_USER;
		};
		if (!allowed) {
			throw new UserException(UserErrorCode.LOGIN_FAILED);
		}
	}
}
