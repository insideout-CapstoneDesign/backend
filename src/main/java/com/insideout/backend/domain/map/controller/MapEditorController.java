package com.insideout.backend.domain.map.controller;

import com.insideout.backend.domain.ai.exception.AiErrorCode;
import com.insideout.backend.domain.ai.exception.AiException;
import com.insideout.backend.domain.map.dto.response.MapEditorInitBuildingDraftResponseDTO;
import com.insideout.backend.domain.map.dto.response.MapEditorInitResponseDTO;
import com.insideout.backend.domain.map.service.MapEditorService;
import com.insideout.backend.domain.tenant.facade.TenantQueryFacade;
import com.insideout.backend.global.apiPayload.ApiResponse;
import com.insideout.backend.global.apiPayload.code.GeneralSuccessCode;
import com.insideout.backend.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Map Editor", description = "맵 에디터 초기 조회 및 draft 부트스트랩 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/map-editor")
public class MapEditorController {

    private final MapEditorService mapEditorService;
    private final TenantQueryFacade tenantQueryFacade;

    @Operation(summary = "맵 에디터 초기 데이터 조회", description = "draft가 없으면 AI 결과를 draft 정식 테이블로 변환한 뒤, floor 기준 에디터 초기 데이터를 반환합니다.")
    @GetMapping("/buildings/{buildingId}/floors/{floorId}")
    public ApiResponse<MapEditorInitResponseDTO> getMapEditorFloor(
            @PathVariable UUID buildingId,
            @PathVariable UUID floorId,
            @RequestParam UUID tenantId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        validateTenantAccess(userDetails, tenantId);
        return ApiResponse.success(
                GeneralSuccessCode.OK,
                mapEditorService.getOrInitializeFloorDraft(
                        tenantId,
                        buildingId,
                        floorId,
                        userDetails != null ? userDetails.getUserId() : null
                )
        );
    }

    @Operation(summary = "건물 draft 일괄 초기화", description = "draft가 없으면 생성하고, 분석 완료된 모든 층을 정식 draft 테이블로 한 번에 초기화합니다.")
    @PostMapping("/buildings/{buildingId}/initialize-draft")
    public ApiResponse<MapEditorInitBuildingDraftResponseDTO> initializeBuildingDraft(
            @PathVariable UUID buildingId,
            @RequestParam UUID tenantId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        validateTenantAccess(userDetails, tenantId);
        return ApiResponse.success(
                GeneralSuccessCode.OK,
                mapEditorService.initializeBuildingDraft(
                        tenantId,
                        buildingId,
                        userDetails != null ? userDetails.getUserId() : null
                )
        );
    }

    private void validateTenantAccess(CustomUserDetails userDetails, UUID tenantId) {
        if (userDetails == null || !tenantQueryFacade.isUserMemberOfTenant(userDetails.getUserId(), tenantId)) {
            throw new AiException(AiErrorCode.AI_TENANT_NOT_FOUND);
        }
    }
}
