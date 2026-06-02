package com.insideout.backend.domain.map.entity;

import com.insideout.backend.domain.building.entity.Floor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Geometry;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "floorplan_object")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FloorplanObject {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne
    @JoinColumn(name = "map_version_id", nullable = false)
    private MapVersion mapVersion;

    @ManyToOne
    @JoinColumn(name = "floor_id", nullable = false)
    private Floor floor;

    @Column(nullable = false)
    private String kind;

    @Column(name = "geom_px", columnDefinition = "geometry", nullable = false)
    private Geometry geomPx;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> properties;

    @Column(nullable = false)
    private String source;

    @Column(name = "ai_detection_id")
    private UUID aiDetectionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onPrePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.properties == null) {
            this.properties = Map.of();
        }
        if (this.source == null) {
            this.source = "manual";
        }
    }

    @PreUpdate
    void onPreUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    @Builder
    public FloorplanObject(
            UUID tenantId,
            MapVersion mapVersion,
            Floor floor,
            String kind,
            Geometry geomPx,
            Map<String, Object> properties,
            String source,
            UUID aiDetectionId
    ) {
        this.tenantId = tenantId;
        this.mapVersion = mapVersion;
        this.floor = floor;
        this.kind = kind;
        this.geomPx = geomPx;
        this.properties = properties != null ? properties : Map.of();
        this.source = source != null ? source : "manual";
        this.aiDetectionId = aiDetectionId;
    }
}
