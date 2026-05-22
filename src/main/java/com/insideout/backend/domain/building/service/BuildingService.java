package com.insideout.backend.domain.building.service;

import com.insideout.backend.domain.building.dto.request.BuildingCreateRequestDTO;
import com.insideout.backend.domain.building.dto.response.BuildingSummaryDTO;
import com.insideout.backend.domain.building.entity.Building;
import com.insideout.backend.domain.building.entity.Campus;
import com.insideout.backend.domain.building.exception.BuildingErrorCode;
import com.insideout.backend.domain.building.exception.BuildingException;
import com.insideout.backend.domain.building.repository.BuildingRepository;
import com.insideout.backend.domain.building.repository.CampusRepository;
import com.insideout.backend.domain.building.entity.Floor;
import com.insideout.backend.domain.building.repository.FloorRepository;
import com.insideout.backend.domain.tenant.entity.Tenant;
import com.insideout.backend.domain.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BuildingService {

    private final BuildingRepository buildingRepository;
    private final CampusRepository campusRepository;
    private final TenantRepository tenantRepository;
    private final FloorRepository floorRepository;

    /**
     * 특정 테넌트에 속한 건물 목록을 최신순으로 조회합니다.
     */
    public List<BuildingSummaryDTO> getBuildings(UUID tenantId) {
        List<Building> buildings = buildingRepository.findByTenant_IdOrderByCreatedAtDesc(tenantId);
        if (buildings.isEmpty()) {
            return List.of();
        }

        List<UUID> buildingIds = buildings.stream()
                .map(Building::getId)
                .toList();

        List<Floor> allFloors = floorRepository.findAllByBuilding_IdInOrderByLevelDesc(buildingIds);
        Map<UUID, List<Floor>> floorsByBuildingId = allFloors.stream()
                .collect(java.util.stream.Collectors.groupingBy(floor -> floor.getBuilding().getId()));

        return buildings.stream()
                .map(building -> BuildingSummaryDTO.from(
                        building,
                        floorsByBuildingId.getOrDefault(building.getId(), List.of())
                ))
                .toList();
    }

    /**
     * 특정 테넌트 하위에 새로운 건물을 생성합니다.
     */
    @Transactional
    public BuildingSummaryDTO createBuilding(UUID tenantId, BuildingCreateRequestDTO req) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BuildingException(BuildingErrorCode.TENANT_NOT_FOUND));

        Campus campus = null;
        if (req.campusId() != null) {
            campus = campusRepository.findByIdAndTenant_Id(req.campusId(), tenantId)
                    .orElseThrow(() -> new BuildingException(BuildingErrorCode.CAMPUS_NOT_FOUND));
        }

        // 위경도 좌표를 JSON meta 맵에 위치 정보로 매핑하여 저장
        Map<String, Object> meta = Map.of();
        if (req.longitude() != null && req.latitude() != null) {
            meta = Map.of("location", Map.of(
                    "longitude", req.longitude(),
                    "latitude", req.latitude()
            ));
        }

        Building building = Building.builder()
                .tenant(tenant)
                .campus(campus)
                .name(req.name())
                .address(req.address())
                .entranceCount(req.entranceCount())
                .externalApiId(req.externalApiId())
                .meta(meta)
                // footprint 등 나머지 필드는 추후 고도화 시 업데이트
                .build();

        Building savedBuilding = buildingRepository.save(building);

        // 건물 등록 시 함께 전달된 층 정보 일괄 저장
        List<Floor> savedFloors = List.of();
        if (req.floors() != null && !req.floors().isEmpty()) {
            List<Floor> floorsToSave = req.floors().stream()
                    .map(f -> Floor.builder()
                            .tenantId(tenantId)
                            .building(savedBuilding)
                            .level(f.level())
                            .name(f.name())
                            .build())
                    .toList();
            savedFloors = floorRepository.saveAll(floorsToSave);
        }

        return BuildingSummaryDTO.from(savedBuilding, savedFloors);
    }
}
