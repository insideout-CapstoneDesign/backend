package com.insideout.backend.domain.building.entity;

import com.insideout.backend.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 층별 실내 도면(Floorplan) 원본 이미지 정보를 담는 엔티티.
 *
 * <p>관리자가 업로드한 원본 도면 이미지를 관리하며,
 * AI 객체 추출 및 맵 에디터 바탕 이미지로 사용됩니다.
 */
@Entity
@Table(name = "floorplan")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Floorplan {

    /**
     * 도면 고유 식별자 (PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * 도면이 속한 테넌트 식별자.
     */
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /**
     * 도면이 속한 층.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id", nullable = false)
    private Floor floor;

    /**
     * S3 또는 스토리지에 저장된 이미지의 URL.
     */
    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    /**
     * 이미지의 해시값(SHA-256).
     * <p>중복 업로드 방지 및 캐싱 최적화에 사용됩니다.
     */
    @Column(name = "image_sha256")
    private String imageSha256;

    /**
     * 원본 이미지의 픽셀 너비.
     * <p>AI 모델이 좌표를 인식하거나 맵 에디터에서 캔버스 크기를 설정할 때 중요합니다.
     */
    @Column(name = "width_px", nullable = false)
    private int widthPx;

    /**
     * 원본 이미지의 픽셀 높이.
     */
    @Column(name = "height_px", nullable = false)
    private int heightPx;

    /**
     * 도면을 업로드한 사용자(관리자).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

    /**
     * 현재 해당 층의 최신(활성화된) 도면인지 여부.
     * <p>같은 층에 여러 도면이 올라갈 수 있으므로(리모델링 등), 이 값이 true인 도면만 서비스됩니다.
     */
    @Column(name = "is_current", nullable = false)
    private boolean isCurrent;

    /**
     * 도면이 시스템에 업로드된 시각.
     */
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private OffsetDateTime uploadedAt;

    @PrePersist
    void onPrePersist() {
        if (this.uploadedAt == null) {
            this.uploadedAt = OffsetDateTime.now();
        }
    }

    @Builder
    public Floorplan(UUID tenantId, Floor floor, String imageUrl, String imageSha256, int widthPx, int heightPx, User uploadedBy, boolean isCurrent) {
        this.tenantId = tenantId;
        this.floor = floor;
        this.imageUrl = imageUrl;
        this.imageSha256 = imageSha256;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        this.uploadedBy = uploadedBy;
        this.isCurrent = isCurrent;
    }
}
