package com.insideout.backend.domain.map.repository;

import com.insideout.backend.domain.map.entity.VerticalConnectorNode;
import com.insideout.backend.domain.map.entity.VerticalConnectorNodeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VerticalConnectorNodeRepository extends JpaRepository<VerticalConnectorNode, VerticalConnectorNodeId> {
}
