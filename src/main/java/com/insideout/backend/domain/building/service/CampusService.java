package com.insideout.backend.domain.building.service;

import com.insideout.backend.domain.building.dto.CoordinateDTO;
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
import java.util.List;
import java.util.UUID;

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
        Point primaryEntrance = createPoint(req.primaryEntrance());

        Campus campus = Campus.builder()
                .tenant(tenant)
                .name(req.name())
                .address(req.address())
                .boundary(boundary)
                .centroid(centroid)
                .primaryEntrance(primaryEntrance)
                .primaryEntranceName(req.primaryEntranceName())
                .meta(req.meta())
                .build();

        Campus savedCampus = campusRepository.save(campus);
        return CampusResponseDTO.from(savedCampus);
    }

    /**
     * 특정 테넌트의 모든 캠퍼스 목록을 최신순으로 조회합니다.
     */
    public List<CampusResponseDTO> getCampuses(UUID tenantId) {
        return campusRepository.findAllByTenant_IdOrderByCreatedAtDesc(tenantId).stream()
                .map(CampusResponseDTO::from)
                .toList();
    }

    /**
     * 특정 테넌트 하위의 캠퍼스 상세 정보를 조회합니다.
     */
    public CampusResponseDTO getCampus(UUID tenantId, UUID campusId) {
        Campus campus = campusRepository.findByIdAndTenant_Id(campusId, tenantId)
                .orElseThrow(() -> new BuildingException(BuildingErrorCode.CAMPUS_NOT_FOUND));
        return CampusResponseDTO.from(campus);
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
        return CampusMapResponseDTO.from(savedMap);
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
