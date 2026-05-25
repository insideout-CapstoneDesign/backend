package com.insideout.backend.domain.building.controller;

import com.insideout.backend.domain.building.exception.BuildingErrorCode;
import com.insideout.backend.domain.building.exception.BuildingException;
import com.insideout.backend.domain.building.dto.request.CampusCreateRequestDTO;
import com.insideout.backend.domain.building.dto.response.CampusMapResponseDTO;
import com.insideout.backend.domain.building.dto.response.CampusResponseDTO;
import com.insideout.backend.domain.building.service.CampusService;
import com.insideout.backend.domain.tenant.facade.TenantQueryFacade;
import com.insideout.backend.global.apiPayload.ApiResponse;
import com.insideout.backend.global.apiPayload.code.GeneralSuccessCode;
import com.insideout.backend.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Tag(name = "Campus", description = "캠퍼스 관리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/campuses")
public class CampusController {

    private final CampusService campusService;
    private final TenantQueryFacade tenantQueryFacade;

    @Operation(summary = "캠퍼스 등록", description = "선택한 테넌트 하위에 새로운 캠퍼스/단지를 등록합니다.")
    @PostMapping
    public ApiResponse<CampusResponseDTO> createCampus(
            @RequestParam UUID tenantId,
            @Valid @RequestBody CampusCreateRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        validateTenantAccess(userDetails, tenantId);
        return ApiResponse.success(
                GeneralSuccessCode.CREATED,
                campusService.createCampus(tenantId, request)
        );
    }

    @Operation(summary = "캠퍼스 목록 조회", description = "특정 테넌트 하위에 등록된 모든 캠퍼스 목록을 조회합니다.")
    @GetMapping
    public ApiResponse<List<CampusResponseDTO>> getCampuses(
            @RequestParam UUID tenantId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        validateTenantAccess(userDetails, tenantId);
        return ApiResponse.success(
                GeneralSuccessCode.OK,
                campusService.getCampuses(tenantId)
        );
    }

    @Operation(summary = "캠퍼스 상세 조회", description = "특정 캠퍼스의 상세 정보를 조회합니다.")
    @GetMapping("/{campusId}")
    public ApiResponse<CampusResponseDTO> getCampus(
            @RequestParam UUID tenantId,
            @PathVariable UUID campusId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        validateTenantAccess(userDetails, tenantId);
        return ApiResponse.success(
                GeneralSuccessCode.OK,
                campusService.getCampus(tenantId, campusId)
        );
    }

    @Operation(summary = "캠퍼스 야외 도면 등록", description = "특정 캠퍼스에 야외 지도 도면 이미지를 업로드합니다.")
    @PostMapping(value = "/{campusId}/maps", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CampusMapResponseDTO> uploadCampusMap(
            @RequestParam UUID tenantId,
            @PathVariable UUID campusId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        // 내부에서 더 상세한 권한 검증 및 테넌트 매핑 검증을 수행하므로 validateTenantAccess 포함
        validateTenantAccess(userDetails, tenantId);
        return ApiResponse.success(
                GeneralSuccessCode.CREATED,
                campusService.uploadCampusMap(tenantId, campusId, file, userDetails)
        );
    }

    private void validateTenantAccess(CustomUserDetails userDetails, UUID tenantId) {
        if (userDetails == null) {
            throw new AuthenticationCredentialsNotFoundException("인증 정보가 없습니다.");
        }

        if (!tenantQueryFacade.isUserMemberOfTenant(userDetails.getUserId(), tenantId)) {
            throw new BuildingException(BuildingErrorCode.UNAUTHORIZED_ACCESS);
        }
    }
}
