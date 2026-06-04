package com.insideout.backend.domain.building.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "building_entrance_mapping")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BuildingEntranceMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "campus_id", nullable = false)
    private UUID campusId;

    @Column(name = "building_id", nullable = false)
    private UUID buildingId;

    @Column(name = "campus_gate_id", nullable = false)
    private String campusGateId;

    @Column(name = "entrance_node_id", nullable = false)
    private UUID entranceNodeId;

    @Column(name = "entrance_poi_id")
    private UUID entrancePoiId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onPrePersist() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }

    @Builder
    public BuildingEntranceMapping(
            UUID tenantId,
            UUID campusId,
            UUID buildingId,
            String campusGateId,
            UUID entranceNodeId,
            UUID entrancePoiId
    ) {
        this.tenantId = tenantId;
        this.campusId = campusId;
        this.buildingId = buildingId;
        this.campusGateId = campusGateId;
        this.entranceNodeId = entranceNodeId;
        this.entrancePoiId = entrancePoiId;
    }

    public void updateEntrance(UUID entranceNodeId, UUID entrancePoiId) {
        this.entranceNodeId = entranceNodeId;
        this.entrancePoiId = entrancePoiId;
    }
}
