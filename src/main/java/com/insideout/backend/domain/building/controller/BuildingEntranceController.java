package com.insideout.backend.domain.building.controller;

import com.insideout.backend.domain.ai.exception.AiErrorCode;
import com.insideout.backend.domain.ai.exception.AiException;
import com.insideout.backend.domain.building.dto.request.BuildingEntranceCreateRequestDTO;
import com.insideout.backend.domain.building.dto.request.BuildingEntranceMappingCreateRequestDTO;
import com.insideout.backend.domain.building.dto.response.BuildingEntranceResponseDTO;
import com.insideout.backend.domain.building.service.BuildingEntranceService;
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

@Tag(name = "Building Entrance", description = "건물 출입구 및 Campus Gate 매핑 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/buildings")
public class BuildingEntranceController {

    private final BuildingEntranceService buildingEntranceService;
    private final TenantQueryFacade tenantQueryFacade;

    @Operation(summary = "건물 출입구 목록 조회", description = "특정 건물에 등록된 출입구 Node/POI 목록과 Gate 매핑 상태를 조회합니다.")
    @GetMapping("/{buildingId}/entrances")
    public ApiResponse<List<BuildingEntranceResponseDTO>> getEntrances(
            @RequestParam UUID tenantId,
            @PathVariable UUID buildingId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        validateTenantAccess(userDetails, tenantId);
        return ApiResponse.success(
                GeneralSuccessCode.OK,
                buildingEntranceService.getEntrances(tenantId, buildingId)
        );
    }

    @Operation(summary = "건물 출입구 생성", description = "건물 1층 등 특정 층에 수동 출입구 Node와 Entrance POI를 함께 생성합니다.")
    @PostMapping("/{buildingId}/entrances")
    public ApiResponse<BuildingEntranceResponseDTO> createEntrance(
            @RequestParam UUID tenantId,
            @PathVariable UUID buildingId,
            @Valid @RequestBody BuildingEntranceCreateRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        validateTenantAccess(userDetails, tenantId);
        return ApiResponse.success(
                GeneralSuccessCode.CREATED,
                buildingEntranceService.createEntrance(tenantId, buildingId, request)
        );
    }

    @Operation(summary = "Campus Gate와 출입구 매핑", description = "Campus Gate와 건물 출입구 Node를 1:1로 연결합니다.")
    @PostMapping("/{buildingId}/entrance-mappings")
    public ApiResponse<BuildingEntranceResponseDTO> mapCampusGate(
            @RequestParam UUID tenantId,
            @PathVariable UUID buildingId,
            @Valid @RequestBody BuildingEntranceMappingCreateRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        validateTenantAccess(userDetails, tenantId);
        return ApiResponse.success(
                GeneralSuccessCode.CREATED,
                buildingEntranceService.mapCampusGate(tenantId, buildingId, request)
        );
    }

    private void validateTenantAccess(CustomUserDetails userDetails, UUID tenantId) {
        if (userDetails == null) {
            throw new AuthenticationCredentialsNotFoundException("인증 정보가 없습니다.");
        }

        if (!tenantQueryFacade.isUserMemberOfTenant(userDetails.getUserId(), tenantId)) {
            throw new AiException(AiErrorCode.AI_TENANT_NOT_FOUND);
        }
    }
}
