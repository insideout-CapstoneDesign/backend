package com.insideout.backend.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.insideout.backend.domain.user.dto.request.LoginRequest;
import com.insideout.backend.domain.user.dto.request.SignupRequest;
import com.insideout.backend.domain.user.dto.response.LoginResponse;
import com.insideout.backend.domain.user.entity.User;
import com.insideout.backend.domain.user.enums.AuthPortalType;
import com.insideout.backend.domain.user.enums.GlobalRole;
import com.insideout.backend.domain.user.exception.UserErrorCode;
import com.insideout.backend.domain.user.exception.UserException;
import com.insideout.backend.domain.user.repository.UserRepository;
import com.insideout.backend.global.security.jwt.JwtTokenProvider;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private JwtTokenProvider jwtTokenProvider;

	@InjectMocks
	private UserService userService;

	@Test
	void signupUser_assignsEndUserRole() {
		SignupRequest request = new SignupRequest("user@example.com", "Abcd1234!", "usernick");
		when(userRepository.existsByEmail(request.email())).thenReturn(false);
		when(userRepository.existsByDisplayName(request.displayName())).thenReturn(false);
		when(passwordEncoder.encode("Abcd1234!")).thenReturn("encoded-password");
		when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		userService.signupUser(request);

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).saveAndFlush(captor.capture());
		assertThat(captor.getValue().getGlobalRole()).isEqualTo(GlobalRole.END_USER);
	}

	@Test
	void signupTenant_assignsTenantUserRole() {
		SignupRequest request = new SignupRequest("tenant@example.com", "Abcd1234!", "tenant");
		when(userRepository.existsByEmail(request.email())).thenReturn(false);
		when(userRepository.existsByDisplayName(request.displayName())).thenReturn(false);
		when(passwordEncoder.encode("Abcd1234!")).thenReturn("encoded-password");
		when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		userService.signupTenant(request);

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).saveAndFlush(captor.capture());
		assertThat(captor.getValue().getGlobalRole()).isEqualTo(GlobalRole.TENANT_USER);
	}

	@Test
	void login_userPortalWithEndUser_succeeds() {
		LoginRequest request = new LoginRequest("user@example.com", "Abcd1234!", AuthPortalType.USER);
		User user = User.builder()
			.email("user@example.com")
			.passwordHash("hashed")
			.displayName("user")
			.globalRole(GlobalRole.END_USER)
			.build();

		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("Abcd1234!", "hashed")).thenReturn(true);
		when(jwtTokenProvider.generateAccessToken("user@example.com")).thenReturn("access-token");
		when(jwtTokenProvider.generateRefreshToken("user@example.com")).thenReturn("refresh-token");

		LoginResponse response = userService.login(request);

		assertThat(response.accessToken()).isEqualTo("access-token");
		assertThat(response.refreshToken()).isEqualTo("refresh-token");
	}

	@Test
	void login_userPortalWithTenantUser_failsAsLoginFailed() {
		LoginRequest request = new LoginRequest("tenant@example.com", "Abcd1234!", AuthPortalType.USER);
		User user = User.builder()
			.email("tenant@example.com")
			.passwordHash("hashed")
			.displayName("tenant")
			.globalRole(GlobalRole.TENANT_USER)
			.build();

		when(userRepository.findByEmail("tenant@example.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("Abcd1234!", "hashed")).thenReturn(true);

		assertThatThrownBy(() -> userService.login(request))
			.isInstanceOf(UserException.class)
			.extracting(ex -> ((UserException) ex).getErrorCode())
			.isEqualTo(UserErrorCode.LOGIN_FAILED);
		verify(jwtTokenProvider, never()).generateAccessToken(any());
		verify(jwtTokenProvider, never()).generateRefreshToken(any());
	}
}
