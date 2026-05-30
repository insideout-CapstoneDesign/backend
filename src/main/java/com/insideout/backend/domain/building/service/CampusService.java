package com.insideout.backend.domain.building.service;

import com.insideout.backend.domain.building.dto.CoordinateDTO;
import com.insideout.backend.domain.building.dto.CampusGateDTO;
import com.insideout.backend.domain.building.dto.request.CampusCreateRequestDTO;
import com.insideout.backend.domain.building.dto.response.CampusMapResponseDTO;
import com.insideout.backend.domain.building.dto.response.CampusResponseDTO;
import com.insideout.backend.domain.building.entity.Campus;
import com.insideout.backend.domain.building.entity.CampusMap;
import com.insideout.backend.domain.building.exception.BuildingErrorCode;
import com.insideout.backend.domain.building.exception.BuildingException;
import com.insideout.backend.domain.building.repository.CampusMapRepository;
import com.insideout.backend.domain.building.repository.CampusRepository;
import com.insideout.backend.domain.tenant.entity.Tenant;
import com.insideout.backend.domain.tenant.facade.TenantQueryFacade;
import com.insideout.backend.domain.tenant.repository.TenantRepository;
import com.insideout.backend.domain.user.entity.User;
import com.insideout.backend.domain.user.repository.UserRepository;
import com.insideout.backend.global.apiPayload.code.GeneralErrorCode;
import com.insideout.backend.global.apiPayload.exception.ProjectException;
import com.insideout.backend.global.infra.storage.service.ImageStorageService;
import java.util.Objects;
import com.insideout.backend.global.infra.storage.service.S3StorageService;
import com.insideout.backend.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CampusService {

    private final CampusRepository campusRepository;
    private final CampusMapRepository campusMapRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final ImageStorageService imageStorageService;
    private final S3StorageService s3StorageService;
    private final TenantQueryFacade tenantQueryFacade;

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    /**
     * 특정 테넌트 하위에 새로운 캠퍼스를 생성합니다.
     */
    @Transactional
    public CampusResponseDTO createCampus(UUID tenantId, CampusCreateRequestDTO req) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BuildingException(BuildingErrorCode.TENANT_NOT_FOUND));

        Polygon boundary = createPolygon(req.boundary());
        Point centroid = createPoint(req.centroid());
        List<CampusGateDTO> normalizedGates = normalizeGates(req);
        Point primaryEntrance = !normalizedGates.isEmpty()
                ? createPoint(normalizedGates.get(0).location())
                : createPoint(req.primaryEntrance());
        String primaryEntranceName = !normalizedGates.isEmpty()
                ? normalizedGates.get(0).name()
                : req.primaryEntranceName();

        Campus campus = Campus.builder()
                .tenant(tenant)
                .name(req.name())
                .address(req.address())
                .boundary(boundary)
                .centroid(centroid)
                .primaryEntrance(primaryEntrance)
                .primaryEntranceName(primaryEntranceName)
                .meta(buildCampusMeta(req.meta(), normalizedGates, req.requiresFloorplan()))
                .build();

        Campus savedCampus = campusRepository.save(campus);
        return CampusResponseDTO.from(savedCampus);
    }

    @Transactional
    public CampusResponseDTO updateCampus(UUID tenantId, UUID campusId, CampusCreateRequestDTO req) {
        Campus campus = campusRepository.findByIdAndTenant_Id(campusId, tenantId)
                .orElseThrow(() -> new BuildingException(BuildingErrorCode.CAMPUS_NOT_FOUND));

        Polygon boundary = createPolygon(req.boundary());
        Point centroid = createPoint(req.centroid());
        List<CampusGateDTO> normalizedGates = normalizeGates(req);
        Point primaryEntrance = !normalizedGates.isEmpty()
                ? createPoint(normalizedGates.get(0).location())
                : createPoint(req.primaryEntrance());
        String primaryEntranceName = !normalizedGates.isEmpty()
                ? normalizedGates.get(0).name()
                : req.primaryEntranceName();

        campus.updateGeography(
                req.name(),
                req.address(),
                boundary,
                centroid,
                primaryEntrance,
                primaryEntranceName,
                buildCampusMeta(req.meta(), normalizedGates, req.requiresFloorplan())
        );

        return CampusResponseDTO.from(campus);
    }

    /**
     * 특정 테넌트의 모든 캠퍼스 목록을 최신순으로 조회합니다.
     */
    public List<CampusResponseDTO> getCampuses(UUID tenantId) {
        List<Campus> campuses = campusRepository.findAllByTenant_IdOrderByCreatedAtDesc(tenantId);
        List<UUID> campusIds = campuses.stream()
                .map(Campus::getId)
                .toList();
        Map<UUID, CampusMap> currentMapsByCampusId = campusMapRepository
                .findAllByCampusIdInAndIsCurrentTrue(campusIds)
                .stream()
                .collect(Collectors.toMap(campusMap -> campusMap.getCampus().getId(), Function.identity()));

        return campuses.stream()
                .map(campus -> {
                    CampusMap currentMap = currentMapsByCampusId.get(campus.getId());
                    String presignedUrl = currentMap != null ? s3StorageService.getPresignedUrlFromS3Url(currentMap.getImageUrl()) : null;
                    return CampusResponseDTO.from(campus, currentMap, presignedUrl);
                })
                .toList();
    }

    /**
     * 특정 테넌트 하위의 캠퍼스 상세 정보를 조회합니다.
     */
    public CampusResponseDTO getCampus(UUID tenantId, UUID campusId) {
        Campus campus = campusRepository.findByIdAndTenant_Id(campusId, tenantId)
                .orElseThrow(() -> new BuildingException(BuildingErrorCode.CAMPUS_NOT_FOUND));
        CampusMap currentMap = campusMapRepository.findByCampusIdAndIsCurrentTrue(campusId)
                .orElse(null);
        String presignedUrl = currentMap != null ? s3StorageService.getPresignedUrlFromS3Url(currentMap.getImageUrl()) : null;
        return CampusResponseDTO.from(campus, currentMap, presignedUrl);
    }


    /**
     * 캠퍼스 야외 도면 이미지를 검증 및 업로드하고 DB에 저장합니다.
     * 비관적 락을 통해 동일 캠퍼스에 대한 중복/동시 도면 업로드 시 정합성을 유지합니다.
     */
    @Transactional
    public CampusMapResponseDTO uploadCampusMap(UUID tenantId, UUID campusId, MultipartFile file, CustomUserDetails userDetails) {
        // 1. 권한 및 테넌트 검증
        if (userDetails == null) {
            throw new ProjectException(GeneralErrorCode.UNAUTHORIZED);
        }

        if (!tenantQueryFacade.isUserMemberOfTenant(userDetails.getUserId(), tenantId)) {
            throw new BuildingException(BuildingErrorCode.UNAUTHORIZED_ACCESS);
        }

        // 비관적 락 획득 (동일 캠퍼스에 대한 동시 업로드 방지)
        Campus campus = campusRepository.findByIdAndTenant_IdForUpdate(campusId, tenantId)
                .orElseThrow(() -> new BuildingException(BuildingErrorCode.CAMPUS_NOT_FOUND));

        User uploader = userRepository.findById(userDetails.getUserId())
                .orElseThrow(() -> new ProjectException(GeneralErrorCode.NOT_FOUND));

        // 2. 이미지 치수 검증 및 획득
        int widthPx;
        int heightPx;
        try {
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image == null) {
                throw new BuildingException(BuildingErrorCode.INVALID_FLOORPLAN_DIMENSIONS);
            }
            widthPx = image.getWidth();
            heightPx = image.getHeight();
        } catch (IOException e) {
            throw new BuildingException(BuildingErrorCode.INVALID_FLOORPLAN_DIMENSIONS);
        }

        // 3. S3 업로드 (s3://bucket/key 형식의 URL 반환)
        String imageUrl = imageStorageService.uploadCampusMapImage(tenantId, campusId, file);
        String bucketName = s3StorageService.defaultBucket();
        String objectKey = imageUrl.substring(String.format("s3://%s/", bucketName).length());

        // 4. 기존 최신 도면(isCurrent=true) 비활성화
        campusMapRepository.deactivateCurrentMapsByCampusId(campusId);

        // 5. 새 CampusMap 저장
        CampusMap campusMap = CampusMap.builder()
                .tenantId(tenantId)
                .campus(campus)
                .bucketName(bucketName)
                .objectKey(objectKey)
                .imageUrl(imageUrl)
                .widthPx(widthPx)
                .heightPx(heightPx)
                .uploadedBy(uploader)
                .isCurrent(true)
                .build();

        CampusMap savedMap = campusMapRepository.save(campusMap);
        return CampusMapResponseDTO.from(savedMap, s3StorageService.getPresignedUrlFromS3Url(savedMap.getImageUrl()));
    }

    private List<CampusGateDTO> normalizeGates(CampusCreateRequestDTO req) {
        if (req.gates() != null && !req.gates().isEmpty()) {
            List<CampusGateDTO> gates = req.gates().stream()
                    .map(gate -> new CampusGateDTO(
                            gate.id() != null && !gate.id().isBlank() ? gate.id() : UUID.randomUUID().toString(),
                            gate.name(),
                            gate.location()
                    ))
                    .toList();

            validateUniqueGateNames(gates);
            return gates;
        }

        if (req.primaryEntrance() != null) {
            return List.of(new CampusGateDTO(
                    UUID.randomUUID().toString(),
                    req.primaryEntranceName() != null && !req.primaryEntranceName().isBlank() ? req.primaryEntranceName() : "대표 출입구",
                    req.primaryEntrance()
            ));
        }

        throw new BuildingException(BuildingErrorCode.INVALID_CAMPUS_GATES);
    }

    private void validateUniqueGateNames(List<CampusGateDTO> gates) {
        Set<String> names = new LinkedHashSet<>();
        for (CampusGateDTO gate : gates) {
            String normalizedName = gate.name() == null ? "" : gate.name().trim().toLowerCase();
            if (!names.add(normalizedName)) {
                throw new BuildingException(BuildingErrorCode.DUPLICATE_CAMPUS_GATE_NAME);
            }
        }
    }

    private Map<String, Object> buildCampusMeta(
            Map<String, Object> originalMeta,
            List<CampusGateDTO> gates,
            Boolean requiresFloorplan
    ) {
        Map<String, Object> meta = new LinkedHashMap<>();
        if (originalMeta != null) {
            meta.putAll(originalMeta);
        }

        meta.put("requiresFloorplan", Boolean.TRUE.equals(requiresFloorplan));
        meta.put("gates", gates.stream()
                .map(gate -> Map.of(
                        "id", gate.id(),
                        "name", gate.name(),
                        "location", Map.of(
                                "longitude", gate.location().longitude(),
                                "latitude", gate.location().latitude()
                        )
                ))
                .toList());
        return meta;
    }

    private Polygon createPolygon(List<CoordinateDTO> boundary) {
        if (boundary == null || boundary.isEmpty()) {
            throw new BuildingException(BuildingErrorCode.INVALID_CAMPUS_BOUNDARY);
        }
        int size = boundary.size();
        if (size < 3) {
            throw new BuildingException(BuildingErrorCode.INVALID_CAMPUS_BOUNDARY);
        }
        boolean isClosed = Objects.equals(boundary.get(0).longitude(), boundary.get(size - 1).longitude())
                && Objects.equals(boundary.get(0).latitude(), boundary.get(size - 1).latitude());
        int coordSize = isClosed ? size : size + 1;
        Coordinate[] coordinates = new Coordinate[coordSize];
        for (int i = 0; i < size; i++) {
            coordinates[i] = new Coordinate(boundary.get(i).longitude(), boundary.get(i).latitude());
        }
        if (!isClosed) {
            coordinates[size] = new Coordinate(boundary.get(0).longitude(), boundary.get(0).latitude());
        }
        Polygon p = GEOMETRY_FACTORY.createPolygon(coordinates);
        p.setSRID(4326);
        return p;
    }

    private Point createPoint(CoordinateDTO dto) {
        if (dto == null) {
            return null;
        }
        Coordinate coord = new Coordinate(dto.longitude(), dto.latitude());
        Point p = GEOMETRY_FACTORY.createPoint(coord);
        p.setSRID(4326);
        return p;
    }
}
