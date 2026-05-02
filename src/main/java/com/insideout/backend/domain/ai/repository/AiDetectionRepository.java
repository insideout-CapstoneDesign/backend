package com.insideout.backend.domain.ai.repository;

import com.insideout.backend.domain.ai.entity.AiDetection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AiDetectionRepository extends JpaRepository<AiDetection, UUID> {
}
