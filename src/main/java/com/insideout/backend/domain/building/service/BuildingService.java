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
import com.insideout.backend.domain.building.repository.FloorplanRepository;
import com.insideout.backend.domain.building.entity.Floorplan;
import com.insideout.backend.global.infra.storage.service.S3StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
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
    private final FloorplanRepository floorplanRepository;
    private final S3StorageService s3StorageService;

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

        List<UUID> floorIds = allFloors.stream().map(Floor::getId).toList();
        List<Floorplan> currentFloorplans = floorplanRepository.findAllByFloorIdInAndIsCurrentTrue(floorIds);
        Map<UUID, Floorplan> floorplanByFloorId = currentFloorplans.stream()
                .collect(java.util.stream.Collectors.toMap(fp -> fp.getFloor().getId(), fp -> fp));

        Map<UUID, String> presignedUrlByFloorplanId = currentFloorplans.stream()
                .collect(java.util.stream.Collectors.toMap(
                        Floorplan::getId,
                        fp -> s3StorageService.getPresignedUrlFromS3Url(fp.getImageUrl())
                ));

        return buildings.stream()
                .map(building -> BuildingSummaryDTO.from(
                        building,
                        floorsByBuildingId.getOrDefault(building.getId(), List.of()),
                        floorplanByFloorId,
                        presignedUrlByFloorplanId
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

        // 위경도 좌표와 등록 플로우 관련 메타를 JSON에 함께 저장
        Map<String, Object> meta = new LinkedHashMap<>();
        if (req.longitude() != null && req.latitude() != null) {
            meta.put("location", Map.of(
                    "longitude", req.longitude(),
                    "latitude", req.latitude()
            ));
        }
        meta.put("requiresFloorplan", Boolean.TRUE.equals(req.requiresFloorplan()));
        meta.put("activationStatus", "draft");

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

        // 건물 등록 시 함께 전달된 층 정보는 도면 업로드 여부와 관계없이 기본 구조로 먼저 저장합니다.
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
