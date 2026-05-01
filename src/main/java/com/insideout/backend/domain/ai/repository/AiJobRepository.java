package com.insideout.backend.domain.ai.repository;

import com.insideout.backend.domain.ai.entity.AiJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AiJobRepository extends JpaRepository<AiJob, UUID> {
}
