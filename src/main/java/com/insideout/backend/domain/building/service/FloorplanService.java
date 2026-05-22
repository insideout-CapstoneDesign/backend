package com.insideout.backend.domain.building.service;

import com.insideout.backend.domain.building.dto.response.FloorplanResponseDTO;
import com.insideout.backend.domain.building.entity.Building;
import com.insideout.backend.domain.building.entity.Floor;
import com.insideout.backend.domain.building.entity.Floorplan;
import com.insideout.backend.domain.building.exception.BuildingErrorCode;
import com.insideout.backend.domain.building.exception.BuildingException;
import com.insideout.backend.domain.building.repository.BuildingRepository;
import com.insideout.backend.domain.building.repository.FloorRepository;
import com.insideout.backend.domain.building.repository.FloorplanRepository;
import com.insideout.backend.domain.tenant.facade.TenantQueryFacade;
import com.insideout.backend.domain.user.entity.User;
import com.insideout.backend.domain.user.repository.UserRepository;
import com.insideout.backend.global.apiPayload.code.GeneralErrorCode;
import com.insideout.backend.global.apiPayload.exception.ProjectException;
import com.insideout.backend.global.infra.storage.service.ImageStorageService;
import com.insideout.backend.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FloorplanService {

    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final FloorplanRepository floorplanRepository;
    private final UserRepository userRepository;
    private final ImageStorageService imageStorageService;
    private final TenantQueryFacade tenantQueryFacade;

    /**
     * 도면 이미지를 검증 및 업로드하고 DB에 저장합니다.
     */
    @Transactional
    public FloorplanResponseDTO uploadFloorplan(UUID tenantId, UUID buildingId, UUID floorId, MultipartFile file, CustomUserDetails userDetails) {
        // 1. 권한 및 테넌트 검증
        if (userDetails == null) {
            throw new ProjectException(GeneralErrorCode.UNAUTHORIZED);
        }

        if (!tenantQueryFacade.isUserMemberOfTenant(userDetails.getUserId(), tenantId)) {
            throw new BuildingException(BuildingErrorCode.UNAUTHORIZED_ACCESS);
        }

        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new BuildingException(BuildingErrorCode.BUILDING_NOT_FOUND));

        if (!building.getTenant().getId().equals(tenantId)) {
            throw new BuildingException(BuildingErrorCode.UNAUTHORIZED_ACCESS);
        }

        Floor floor = floorRepository.findByIdAndBuilding_IdForUpdate(floorId, buildingId)
                .orElseThrow(() -> new BuildingException(BuildingErrorCode.FLOOR_NOT_FOUND));

        if (!floor.getTenantId().equals(tenantId)) {
            throw new BuildingException(BuildingErrorCode.UNAUTHORIZED_ACCESS);
        }

        User uploader = userRepository.findById(userDetails.getUserId())
                .orElseThrow(() -> new ProjectException(GeneralErrorCode.NOT_FOUND));

        // 2. 이미지 치수 (가로/세로 픽셀) 검증 및 획득
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

        // 3. 파일의 SHA-256 해시값 계산
        String sha256 = calculateSha256(file);

        // 4. S3 업로드 (s3://bucket/key 형식의 URL 반환)
        String imageUrl = imageStorageService.uploadFloorplanImage(tenantId, buildingId, floorId, file);

        // 5. 기존 최신 도면(isCurrent=true) 비활성화
        floorplanRepository.deactivateCurrentFloorplansByFloorId(floorId);

        // 6. 새 Floorplan 엔티티 저장
        Floorplan floorplan = Floorplan.builder()
                .tenantId(tenantId)
                .floor(floor)
                .imageUrl(imageUrl)
                .imageSha256(sha256)
                .widthPx(widthPx)
                .heightPx(heightPx)
                .uploadedBy(uploader)
                .isCurrent(true)
                .build();

        Floorplan savedFloorplan = floorplanRepository.save(floorplan);

        return FloorplanResponseDTO.from(savedFloorplan);
    }

    private String calculateSha256(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(file.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new ProjectException(GeneralErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
