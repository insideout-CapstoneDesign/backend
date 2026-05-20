package com.insideout.backend.domain.tenant.controller;

import com.insideout.backend.domain.tenant.dto.TenantSummaryResDTO;
import com.insideout.backend.domain.tenant.service.TenantService;
import com.insideout.backend.global.apiPayload.ApiResponse;
import com.insideout.backend.global.apiPayload.code.GeneralSuccessCode;
import com.insideout.backend.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import com.insideout.backend.domain.tenant.dto.TenantCreateReqDTO;

import java.util.List;

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
    @PostMapping
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
}
