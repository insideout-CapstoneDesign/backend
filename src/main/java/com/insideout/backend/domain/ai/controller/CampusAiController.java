package com.insideout.backend.domain.ai.controller;

import com.insideout.backend.domain.ai.dto.response.CampusAnalyzeResultDTO;
import com.insideout.backend.domain.ai.dto.response.CampusDetectionViewDTO;
import com.insideout.backend.domain.ai.exception.AiErrorCode;
import com.insideout.backend.domain.ai.exception.AiException;
import com.insideout.backend.domain.ai.service.CampusAiAnalyzeService;
import com.insideout.backend.domain.tenant.facade.TenantQueryFacade;
import com.insideout.backend.global.apiPayload.ApiResponse;
import com.insideout.backend.global.apiPayload.code.GeneralSuccessCode;
import com.insideout.backend.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "AI Campus", description = "캠퍼스 도면 AI 분석 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ai/campuses")
public class CampusAiController {

    private final CampusAiAnalyzeService campusAiAnalyzeService;
    private final TenantQueryFacade tenantQueryFacade;

    @Operation(summary = "캠퍼스 도면 분석 요청", description = "특정 campusMap에 대해 AI 분석을 수행하고 결과를 저장합니다.")
    @PostMapping("/{campusMapId}/analyze")
    public ApiResponse<CampusAnalyzeResultDTO> analyze(
            @PathVariable UUID campusMapId,
            @RequestParam UUID tenantId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        validateTenantAccess(userDetails, tenantId);
        return ApiResponse.success(
                GeneralSuccessCode.OK,
                campusAiAnalyzeService.analyze(campusMapId, tenantId)
        );
    }

    @Operation(summary = "캠퍼스 도면 분석 결과 조회", description = "특정 campusMap에 저장된 AI detection 결과를 조회합니다.")
    @GetMapping("/{campusMapId}/detections")
    public ApiResponse<List<CampusDetectionViewDTO>> getDetections(
            @PathVariable UUID campusMapId,
            @RequestParam UUID tenantId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        validateTenantAccess(userDetails, tenantId);
        return ApiResponse.success(
                GeneralSuccessCode.OK,
                campusAiAnalyzeService.getDetections(campusMapId, tenantId)
        );
    }

    private void validateTenantAccess(CustomUserDetails userDetails, UUID tenantId) {
        if (userDetails == null) {
            throw new AiException(AiErrorCode.AI_TENANT_NOT_FOUND);
        }

        if (!tenantQueryFacade.isUserMemberOfTenant(userDetails.getUserId(), tenantId)) {
            throw new AiException(AiErrorCode.AI_TENANT_NOT_FOUND);
        }
    }
}
