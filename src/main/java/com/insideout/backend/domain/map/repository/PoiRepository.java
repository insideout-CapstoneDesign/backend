package com.insideout.backend.domain.map.repository;

import com.insideout.backend.domain.map.entity.Poi;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PoiRepository extends JpaRepository<Poi, UUID> {

    Optional<Poi> findByPublicId(Long publicId);
}
