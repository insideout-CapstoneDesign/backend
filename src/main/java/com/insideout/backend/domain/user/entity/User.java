package com.insideout.backend.domain.user.entity;

import com.insideout.backend.domain.user.enums.GlobalRole;
import com.insideout.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "app_user")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, unique = true, columnDefinition = "citext")
	private String email;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(name = "display_name", nullable = false)
	private String displayName;

	@Enumerated(EnumType.STRING)
	@Column(name = "global_role", nullable = false)
	private GlobalRole globalRole;

	@Builder
	public User(String email, String passwordHash, String displayName, GlobalRole globalRole) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.displayName = displayName;
		this.globalRole = (globalRole == null) ? GlobalRole.END_USER : globalRole;
	}
}
