package com.insideout.backend.domain.user.service;

import com.insideout.backend.domain.user.dto.response.CurrentUserResponse;
import com.insideout.backend.domain.user.enums.GlobalRole;
import com.insideout.backend.global.security.CustomUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserQueryServiceTest {

	private final UserQueryService userQueryService = new UserQueryService();

	@Test
	void getCurrentUser_returnsDisplayNameInResponse() {
		UUID userId = UUID.randomUUID();
		CustomUserDetails userDetails = new CustomUserDetails(
			userId,
			"test@example.com",
			"passwordHash",
			"tester",
			GlobalRole.END_USER
		);

		CurrentUserResponse response = userQueryService.getCurrentUser(userDetails);

		assertThat(response.userId()).isEqualTo(userId);
		assertThat(response.email()).isEqualTo("test@example.com");
		assertThat(response.displayName()).isEqualTo("tester");
		assertThat(response.globalRole()).isEqualTo(GlobalRole.END_USER);
	}

	@Test
	void getCurrentUser_throwsWhenPrincipalIsNull() {
		assertThatThrownBy(() -> userQueryService.getCurrentUser(null))
			.isInstanceOf(AuthenticationCredentialsNotFoundException.class);
	}
}

