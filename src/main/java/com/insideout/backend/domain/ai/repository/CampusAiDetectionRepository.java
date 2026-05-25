package com.insideout.backend.domain.ai.repository;

import com.insideout.backend.domain.ai.entity.CampusAiDetection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CampusAiDetectionRepository extends JpaRepository<CampusAiDetection, UUID> {
    List<CampusAiDetection> findByJob_IdOrderByIdAsc(UUID jobId);
    void deleteByCampusMap_Id(UUID campusMapId);
}
