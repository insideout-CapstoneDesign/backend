package com.insideout.backend.domain.building.entity;

import com.insideout.backend.domain.map.entity.MapVersion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "building_entrance_mapping",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_building_entrance_mapping_gate",
                        columnNames = {"tenant_id", "campus_id", "map_version_id", "campus_gate_id"}
                ),
                @UniqueConstraint(
                        name = "uk_building_entrance_mapping_node",
                        columnNames = {"tenant_id", "building_id", "map_version_id", "entrance_node_id"}
                )
        }
)
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "map_version_id", nullable = false)
    private MapVersion mapVersion;

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
            MapVersion mapVersion,
            String campusGateId,
            UUID entranceNodeId,
            UUID entrancePoiId
    ) {
        this.tenantId = tenantId;
        this.campusId = campusId;
        this.buildingId = buildingId;
        this.mapVersion = mapVersion;
        this.campusGateId = campusGateId;
        this.entranceNodeId = entranceNodeId;
        this.entrancePoiId = entrancePoiId;
    }

    public void updateEntrance(UUID entranceNodeId, UUID entrancePoiId) {
        this.entranceNodeId = entranceNodeId;
        this.entrancePoiId = entrancePoiId;
    }
}
