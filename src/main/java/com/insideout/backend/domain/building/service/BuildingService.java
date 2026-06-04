package com.insideout.backend.domain.building.service;

import com.insideout.backend.domain.ai.repository.AiDetectionRepository;
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
import com.insideout.backend.domain.map.enums.MapType;
import com.insideout.backend.domain.map.repository.MapVersionRepository;
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
import java.util.Objects;
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
    private final AiDetectionRepository aiDetectionRepository;
    private final S3StorageService s3StorageService;
    private final MapVersionRepository mapVersionRepository;

    /**
     * 특정 테넌트에 속한 건물 목록을 최신순으로 조회합니다.
     */
    public List<BuildingSummaryDTO> getBuildings(UUID tenantId) {
        List<Building> buildings = buildingRepository.findByTenant_IdOrderByCreatedAtDesc(tenantId);
        if (buildings.isEmpty()) {
            return List.of();
        }

        return buildBuildingSummaries(buildings);
    }

    /**
     * 특정 테넌트에 속한 건물 단건을 조회합니다.
     */
    public BuildingSummaryDTO getBuilding(UUID tenantId, UUID buildingId) {
        Building building = buildingRepository.findByIdAndTenant_Id(buildingId, tenantId)
                .orElseThrow(() -> new BuildingException(BuildingErrorCode.BUILDING_NOT_FOUND));

        return buildBuildingSummaries(List.of(building)).stream()
                .findFirst()
                .orElseThrow(() -> new BuildingException(BuildingErrorCode.BUILDING_NOT_FOUND));
    }

    private List<BuildingSummaryDTO> buildBuildingSummaries(List<Building> buildings) {
        List<UUID> buildingIds = buildings.stream()
                .map(Building::getId)
                .toList();
        Map<UUID, Boolean> publishedByBuildingId = mapVersionRepository
                .findBuildingIdsByMapTypeAndStatus(buildingIds, MapType.BUILDING, "published")
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        java.util.function.Function.identity(),
                        ignored -> true,
                        (existing, replacement) -> existing
                ));

        List<Floor> allFloors = floorRepository.findAllByBuilding_IdInOrderByLevelDesc(buildingIds);
        Map<UUID, List<Floor>> floorsByBuildingId = allFloors.stream()
                .collect(java.util.stream.Collectors.groupingBy(floor -> floor.getBuilding().getId()));

        List<UUID> floorIds = allFloors.stream().map(Floor::getId).toList();
        List<Floorplan> currentFloorplans = floorplanRepository.findAllByFloorIdInAndIsCurrentTrue(floorIds);
        Map<UUID, Floorplan> floorplanByFloorId = currentFloorplans.stream()
                .filter(fp -> fp.getFloor() != null && fp.getFloor().getId() != null)
                .collect(java.util.stream.Collectors.toMap(
                        fp -> Objects.requireNonNull(fp.getFloor()).getId(),
                        fp -> fp,
                        (existing, replacement) -> existing
                ));

        Map<UUID, String> presignedUrlByFloorplanId = currentFloorplans.stream()
                .filter(fp -> fp.getImageUrl() != null)
                .collect(java.util.stream.Collectors.toMap(
                        Floorplan::getId,
                        fp -> s3StorageService.getPresignedUrlFromS3Url(fp.getImageUrl()),
                        (existing, replacement) -> existing
                ));

        List<UUID> floorplanIds = currentFloorplans.stream()
                .map(Floorplan::getId)
                .toList();
        Map<UUID, Boolean> analysisCompletedByFloorplanId = aiDetectionRepository
                .findAnalyzedFloorplanIdsByTenantIdAndFloorplanIds(
                        buildings.get(0).getTenant().getId(),
                        floorplanIds
                ).stream()
                .collect(java.util.stream.Collectors.toMap(
                        java.util.function.Function.identity(),
                        ignored -> true,
                        (existing, replacement) -> existing
                ));

        return buildings.stream()
                .map(building -> BuildingSummaryDTO.from(
                        building,
                        floorsByBuildingId.getOrDefault(building.getId(), List.of()),
                        floorplanByFloorId,
                        presignedUrlByFloorplanId,
                        analysisCompletedByFloorplanId,
                        Boolean.TRUE.equals(publishedByBuildingId.get(building.getId()))
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

    /**
     * 건물에 층을 추가합니다.
     */
    @Transactional
    public BuildingSummaryDTO addFloor(UUID tenantId, UUID buildingId, com.insideout.backend.domain.building.dto.request.FloorRequestDTO req) {
        Building building = buildingRepository.findByIdAndTenant_Id(buildingId, tenantId)
                .orElseThrow(() -> new BuildingException(BuildingErrorCode.BUILDING_NOT_FOUND));

        // 해당 건물에 동일한 level 또는 name이 있는지 확인
        boolean exists = floorRepository.findAllByBuilding_IdInOrderByLevelDesc(List.of(buildingId)).stream()
                .anyMatch(f -> f.getLevel() == req.level() || f.getName().equalsIgnoreCase(req.name()));
        if (exists) {
            throw new BuildingException(BuildingErrorCode.DUPLICATE_FLOOR);
        }

        Floor floor = Floor.builder()
                .tenantId(tenantId)
                .building(building)
                .level(req.level())
                .name(req.name())
                .build();

        floorRepository.save(floor);

        return getBuilding(tenantId, buildingId);
    }
}
