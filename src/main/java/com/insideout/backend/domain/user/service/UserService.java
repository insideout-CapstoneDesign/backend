package com.insideout.backend.domain.user.service;

import com.insideout.backend.domain.user.dto.request.SignupRequest;
import com.insideout.backend.domain.user.dto.response.SignupResponse;
import com.insideout.backend.domain.user.entity.User;
import com.insideout.backend.domain.user.enums.GlobalRole;
import com.insideout.backend.domain.user.exception.UserErrorCode;
import com.insideout.backend.domain.user.exception.UserException;
import com.insideout.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	@Transactional
	public SignupResponse signup(SignupRequest request) {
		if (userRepository.existsByEmail(request.email())) {
			throw new UserException(UserErrorCode.EMAIL_ALREADY_EXISTS);
		}

		User user = User.builder()
			.email(request.email())
			.passwordHash(passwordEncoder.encode(request.password()))
			.displayName(request.displayName())
			.globalRole(GlobalRole.END_USER)
			.build();

		User saved = userRepository.save(user);

		return new SignupResponse(
			saved.getId(),
			saved.getEmail(),
			saved.getDisplayName(),
			saved.getGlobalRole()
		);
	}
}
