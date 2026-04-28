package com.insideout.backend.domain.user.repository;

import com.insideout.backend.domain.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
	boolean existsByEmail(String email);
	Optional<User> findByEmail(String email);
}
