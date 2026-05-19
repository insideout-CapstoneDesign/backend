package com.insideout.backend.domain.building.entity;

import com.insideout.backend.domain.building.exception.BuildingErrorCode;
import com.insideout.backend.domain.building.exception.BuildingException;
import com.insideout.backend.domain.user.entity.User;
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

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 캠퍼스/단지 전체 도면 이미지 메타데이터.
 */
@Entity
@Table(name = "campus_map")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CampusMap {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campus_id", nullable = false)
    private Campus campus;

    @Column(name = "bucket_name", nullable = false)
    private String bucketName;

    @Column(name = "object_key", nullable = false)
    private String objectKey;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "width_px", nullable = false)
    private int widthPx;

    @Column(name = "height_px", nullable = false)
    private int heightPx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

    @Column(name = "is_current", nullable = false)
    private boolean isCurrent;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private OffsetDateTime uploadedAt;

    @PrePersist
    void onPrePersist() {
        if (this.uploadedAt == null) {
            this.uploadedAt = OffsetDateTime.now();
        }
    }

    @Builder
    public CampusMap(UUID tenantId, Campus campus, String bucketName, String objectKey, String imageUrl,
                     int widthPx, int heightPx, User uploadedBy, boolean isCurrent) {
        if (hasDifferentTenant(tenantId, campus)) {
            throw new BuildingException(BuildingErrorCode.CAMPUS_MAP_TENANT_MISMATCH);
        }
        this.tenantId = tenantId;
        this.campus = campus;
        this.bucketName = bucketName;
        this.objectKey = objectKey;
        this.imageUrl = imageUrl;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        this.uploadedBy = uploadedBy;
        this.isCurrent = isCurrent;
    }

    private boolean hasDifferentTenant(UUID tenantId, Campus campus) {
        if (tenantId == null || campus == null || campus.getTenant() == null) {
            return false;
        }

        UUID campusTenantId = campus.getTenant().getId();
        return campusTenantId != null && !Objects.equals(tenantId, campusTenantId);
    }
}
