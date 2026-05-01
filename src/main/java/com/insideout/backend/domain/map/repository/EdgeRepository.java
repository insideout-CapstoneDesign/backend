package com.insideout.backend.domain.map.repository;

import com.insideout.backend.domain.map.entity.Edge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface EdgeRepository extends JpaRepository<Edge, UUID> {
}
