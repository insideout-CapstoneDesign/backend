package com.insideout.backend.domain.building.controller;

import com.insideout.backend.domain.ai.exception.AiErrorCode;
import com.insideout.backend.domain.ai.exception.AiException;
import com.insideout.backend.domain.building.dto.request.BuildingCreateRequestDTO;
import com.insideout.backend.domain.building.dto.request.FloorRequestDTO;
import com.insideout.backend.domain.building.dto.response.BuildingSummaryDTO;
import com.insideout.backend.domain.building.service.BuildingService;
import com.insideout.backend.domain.tenant.facade.TenantQueryFacade;
import com.insideout.backend.global.apiPayload.ApiResponse;
import com.insideout.backend.global.apiPayload.code.GeneralSuccessCode;
import com.insideout.backend.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Building", description = "건물 관리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/buildings")
public class BuildingController {

    private final BuildingService buildingService;
    private final TenantQueryFacade tenantQueryFacade;

    @Operation(summary = "테넌트 내 건물 목록 조회", description = "특정 테넌트 하위에 등록된 건물 목록을 조회합니다.")
    @GetMapping
    public ApiResponse<List<BuildingSummaryDTO>> getBuildings(
            @RequestParam UUID tenantId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        validateTenantAccess(userDetails, tenantId);
        return ApiResponse.success(
                GeneralSuccessCode.OK,
                buildingService.getBuildings(tenantId)
        );
    }

    @Operation(summary = "테넌트 내 건물 단건 조회", description = "특정 테넌트 하위의 건물 상세 정보를 조회합니다.")
    @GetMapping("/{buildingId}")
    public ApiResponse<BuildingSummaryDTO> getBuilding(
            @PathVariable UUID buildingId,
            @RequestParam UUID tenantId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        validateTenantAccess(userDetails, tenantId);
        return ApiResponse.success(
                GeneralSuccessCode.OK,
                buildingService.getBuilding(tenantId, buildingId)
        );
    }

    @Operation(summary = "건물 등록", description = "선택한 테넌트 하위에 새로운 건물을 등록합니다.")
    @PostMapping
    public ApiResponse<BuildingSummaryDTO> createBuilding(
            @RequestParam UUID tenantId,
            @Valid @RequestBody BuildingCreateRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        validateTenantAccess(userDetails, tenantId);
        return ApiResponse.success(
                GeneralSuccessCode.CREATED,
                buildingService.createBuilding(tenantId, request)
        );
    }

    @Operation(summary = "건물에 층 추가", description = "특정 건물 하위에 새로운 개별 층을 등록합니다.")
    @PostMapping("/{buildingId}/floors")
    public ApiResponse<BuildingSummaryDTO> addFloor(
            @PathVariable UUID buildingId,
            @RequestParam UUID tenantId,
            @Valid @RequestBody FloorRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        validateTenantAccess(userDetails, tenantId);
        return ApiResponse.success(
                GeneralSuccessCode.CREATED,
                buildingService.addFloor(tenantId, buildingId, request)
        );
    }

    @Operation(summary = "건물 비활성화", description = "건물을 비활성화(inactive) 상태로 변경합니다.")
    @PostMapping("/{buildingId}/deactivate")
    public ApiResponse<BuildingSummaryDTO> deactivateBuilding(
            @PathVariable UUID buildingId,
            @RequestParam UUID tenantId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        validateTenantAccess(userDetails, tenantId);
        return ApiResponse.success(
                GeneralSuccessCode.OK,
                buildingService.deactivateBuilding(tenantId, buildingId)
        );
    }

    @Operation(summary = "건물 활성화", description = "건물을 활성화(active) 상태로 변경합니다.")
    @PostMapping("/{buildingId}/activate")
    public ApiResponse<BuildingSummaryDTO> activateBuilding(
            @PathVariable UUID buildingId,
            @RequestParam UUID tenantId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        validateTenantAccess(userDetails, tenantId);
        return ApiResponse.success(
                GeneralSuccessCode.OK,
                buildingService.activateBuilding(tenantId, buildingId)
        );
    }

    /**
     * 로그인한 유저가 요청한 tenantId에 소속되어 있는지 검증합니다.
     */
    private void validateTenantAccess(CustomUserDetails userDetails, UUID tenantId) {
        if (userDetails == null) {
            throw new AuthenticationCredentialsNotFoundException("인증 정보가 없습니다.");
        }

        if (!tenantQueryFacade.isUserMemberOfTenant(userDetails.getUserId(), tenantId)) {
            // TODO: 추후 Tenant 도메인으로 예외를 통합하는 것이 좋습니다. 현재는 AiException을 재사용합니다.
            throw new AiException(AiErrorCode.AI_TENANT_NOT_FOUND);
        }
    }
}
