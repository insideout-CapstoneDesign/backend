package com.insideout.backend.domain.tenant.controller;

import com.insideout.backend.domain.tenant.dto.response.TenantSummaryResDTO;
import com.insideout.backend.domain.tenant.dto.request.TenantCreateReqDTO;
import com.insideout.backend.domain.tenant.service.TenantService;
import com.insideout.backend.global.apiPayload.ApiResponse;
import com.insideout.backend.global.apiPayload.code.GeneralSuccessCode;
import com.insideout.backend.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Tenant", description = "테넌트 관리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final TenantService tenantService;

    @Operation(summary = "내 테넌트 목록 조회", description = "로그인한 유저가 소속된 모든 테넌트 목록을 반환합니다.")
    @GetMapping("/me")
    public ApiResponse<List<TenantSummaryResDTO>> getMyTenants(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            throw new AuthenticationCredentialsNotFoundException("인증 정보가 없습니다.");
        }
        
        return ApiResponse.success(
                GeneralSuccessCode.OK,
                tenantService.getMyTenants(userDetails.getUserId())
        );
    }

    @Operation(summary = "테넌트 등록", description = "새로운 테넌트를 생성하고 현재 로그인한 유저를 관리자로 등록합니다.")
    @PostMapping("/create")
    public ApiResponse<TenantSummaryResDTO> createTenant(
            @Valid @RequestBody TenantCreateReqDTO request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            throw new AuthenticationCredentialsNotFoundException("인증 정보가 없습니다.");
        }

        return ApiResponse.success(
                GeneralSuccessCode.CREATED,
                tenantService.createTenant(userDetails.getUserId(), request)
        );
    }

    @Operation(summary = "테넌트 활성화", description = "대기(pending) 상태인 테넌트를 활성화(approved) 상태로 변경합니다.")
    @PatchMapping("/{tenantId}/activate")
    public ApiResponse<TenantSummaryResDTO> activateTenant(
            @PathVariable UUID tenantId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            throw new AuthenticationCredentialsNotFoundException("인증 정보가 없습니다.");
        }

        return ApiResponse.success(
                GeneralSuccessCode.OK,
                tenantService.activateTenant(userDetails.getUserId(), tenantId)
        );
    }

    @Operation(summary = "테넌트 비활성화 (구독 취소)", description = "테넌트의 상태를 suspended(구독 취소)로 변경합니다.")
    @PatchMapping("/{tenantId}/deactivate")
    public ApiResponse<TenantSummaryResDTO> deactivateTenant(
            @PathVariable UUID tenantId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            throw new AuthenticationCredentialsNotFoundException("인증 정보가 없습니다.");
        }

        return ApiResponse.success(
                GeneralSuccessCode.OK,
                tenantService.deactivateTenant(userDetails.getUserId(), tenantId)
        );
    }
}
