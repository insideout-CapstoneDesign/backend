package com.insideout.backend.domain.map.repository;

import com.insideout.backend.domain.map.entity.VerticalConnectorNode;
import com.insideout.backend.domain.map.entity.VerticalConnectorNodeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VerticalConnectorNodeRepository extends JpaRepository<VerticalConnectorNode, VerticalConnectorNodeId> {

    List<VerticalConnectorNode> findByConnectorMapVersionId(UUID mapVersionId);
}
