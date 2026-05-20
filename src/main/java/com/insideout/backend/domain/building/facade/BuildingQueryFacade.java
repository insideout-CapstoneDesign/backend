package com.insideout.backend.domain.building.facade;

import com.insideout.backend.domain.building.entity.Floorplan;
import com.insideout.backend.domain.building.repository.FloorplanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * 타 도메인에서 Building 도메인의 조회 기능을 안전하게 사용하기 위한 읽기 전용 진입점.
 *
 * <p>Repository를 직접 노출하지 않고, 필요한 조회 규칙을 이 facade에 모읍니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BuildingQueryFacade {

    private final FloorplanRepository floorplanRepository;

    public Optional<Floorplan> findFloorplanForTenant(UUID floorplanId, UUID tenantId) {
        if (floorplanId == null || tenantId == null) {
            return Optional.empty();
        }
        return floorplanRepository.findByIdAndTenantId(floorplanId, tenantId);
    }
}
