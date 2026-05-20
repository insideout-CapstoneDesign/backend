package com.insideout.backend.domain.ai.controller;

import com.insideout.backend.domain.ai.dto.response.AnalyzeResultDTO;
import com.insideout.backend.domain.ai.dto.response.DetectionViewDTO;
import com.insideout.backend.domain.ai.exception.AiErrorCode;
import com.insideout.backend.domain.ai.exception.AiException;
import com.insideout.backend.domain.ai.service.AiAnalyzeService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "AI", description = "AI 도면 분석 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai")
public class AiController {

    private final AiAnalyzeService aiAnalyzeService;
    private final TenantQueryFacade tenantQueryFacade;

    @Operation(summary = "도면 분석 요청", description = "특정 floorplan에 대해 AI 분석을 수행하고 결과를 저장합니다.")
    @PostMapping("/floorplans/{floorplanId}/analyze")
    public ApiResponse<AnalyzeResultDTO> analyze(
            @PathVariable UUID floorplanId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        UUID tenantId = resolveTenantId(userDetails);
        return ApiResponse.success(
                GeneralSuccessCode.OK,
                aiAnalyzeService.analyze(floorplanId, tenantId)
        );
    }

    @Operation(summary = "도면 분석 결과 조회", description = "특정 floorplan에 저장된 AI detection 결과를 조회합니다.")
    @GetMapping("/floorplans/{floorplanId}/detections")
    public ApiResponse<List<DetectionViewDTO>> getDetections(
            @PathVariable UUID floorplanId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        UUID tenantId = resolveTenantId(userDetails);
        return ApiResponse.success(
                GeneralSuccessCode.OK,
                aiAnalyzeService.getDetections(floorplanId, tenantId)
        );
    }

    private UUID resolveTenantId(CustomUserDetails userDetails) {
        if (userDetails == null) {
            throw new AiException(AiErrorCode.AI_TENANT_NOT_FOUND);
        }

        return tenantQueryFacade.findPrimaryTenantIdByUserId(userDetails.getUserId())
                .orElseThrow(() -> new AiException(AiErrorCode.AI_TENANT_NOT_FOUND));
    }
}
