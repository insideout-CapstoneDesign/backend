package com.insideout.backend.domain.building.entity;

import com.insideout.backend.domain.tenant.entity.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 여러 건물을 포함하는 물리적 단지/캠퍼스.
 *
 * <p>Tenant는 관리 조직이고 Campus는 실제 공간입니다. 단독 건물은 Campus 없이 Building만 존재합니다.
 */
@Entity
@Table(name = "campus")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Campus {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false)
    private String name;

    private String address;

    @Column(columnDefinition = "geography(Polygon, 4326)")
    private Polygon boundary;

    @Column(columnDefinition = "geography(Point, 4326)")
    private Point centroid;

    @Column(name = "primary_entrance", columnDefinition = "geography(Point, 4326)", nullable = false)
    private Point primaryEntrance;

    @Column(name = "primary_entrance_name")
    private String primaryEntranceName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> meta;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onPrePersist() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
        if (this.meta == null) {
            this.meta = Map.of();
        }
    }

    @Builder
    public Campus(Tenant tenant, String name, String address, Polygon boundary, Point centroid,
                  Point primaryEntrance, String primaryEntranceName, Map<String, Object> meta) {
        this.tenant = tenant;
        this.name = name;
        this.address = address;
        this.boundary = boundary;
        this.centroid = centroid;
        this.primaryEntrance = primaryEntrance;
        this.primaryEntranceName = primaryEntranceName;
        this.meta = meta != null ? meta : Map.of();
    }
}
