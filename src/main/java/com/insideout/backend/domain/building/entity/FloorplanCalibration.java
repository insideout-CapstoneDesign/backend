package com.insideout.backend.domain.building.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 도면 이미지의 픽셀(Pixel) 좌표를 실제 지구의 위경도(GPS)와 
 * 미터(Meter) 단위로 변환해주는 '마법의 수학 행렬' 정보를 담는 엔티티.
 *
 * <p>관리자가 맵 에디터에서 도면의 실제 위치(기준점)를 찍으면(요구사항 45번),
 * 이 데이터가 생성되어 거리를 계산(length_m)할 수 있게 해줍니다.
 */
@Entity
@Table(name = "floorplan_calibration")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FloorplanCalibration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floorplan_id", nullable = false)
    private Floorplan floorplan;

    /**
     * Ground Control Points (기준점들).
     * <p>사용자가 맵 에디터에서 찍은 "픽셀 좌표 (x,y)"와 "실제 GPS 좌표 (Lat,Lng)"의 쌍을 담습니다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> gcp;

    /**
     * 변환 행렬 (Affine Transform Matrix).
     * <p>이 숫자 배열을 픽셀 좌표에 곱하면 실제 GPS 좌표가 튀어나옵니다.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "double precision[]")
    private List<Double> affine;

    /**
     * 도면이 북쪽을 기준으로 얼마나 돌아가 있는지 (회전 각도).
     */
    @Column(name = "rotation_deg", precision = 10, scale = 2)
    private BigDecimal rotationDeg;

    /**
     * 캘리브레이션 오차율 (미터).
     */
    @Column(name = "rmse_m", precision = 10, scale = 2)
    private BigDecimal rmseM;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onPrePersist() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }

    @Builder
    public FloorplanCalibration(UUID tenantId, Floorplan floorplan, Map<String, Object> gcp, List<Double> affine, BigDecimal rotationDeg, BigDecimal rmseM) {
        this.tenantId = tenantId;
        this.floorplan = floorplan;
        this.gcp = gcp;
        this.affine = affine;
        this.rotationDeg = rotationDeg;
        this.rmseM = rmseM;
    }

    public void updateAffine(List<Double> affine) {
        this.affine = affine;
    }
}
