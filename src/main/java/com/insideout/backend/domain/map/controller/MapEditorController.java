package com.insideout.backend.domain.map.controller;

import com.insideout.backend.domain.ai.exception.AiErrorCode;
import com.insideout.backend.domain.ai.exception.AiException;
import com.insideout.backend.domain.map.dto.request.MapEditorDraftSaveRequestDTO;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

import com.insideout.backend.domain.map.dto.request.MapEditorVerticalConnectorCreateRequestDTO;
import com.insideout.backend.domain.map.dto.request.MapEditorVerticalConnectorUpdateRequestDTO;
import com.insideout.backend.domain.map.dto.request.MapEditorVerticalConnectorMapRequestDTO;
import com.insideout.backend.domain.map.dto.response.MapEditorVerticalConnectorDTO;
import com.insideout.backend.domain.map.dto.response.MapEditorDraftPoiResponseDTO;
import com.insideout.backend.domain.map.dto.request.MapEditorPoiMappingsSaveRequestDTO;
import org.springframework.web.bind.annotation.DeleteMapping;
import java.util.List;
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

    @Operation(summary = "맵 에디터 층 draft 저장", description = "현재 층의 node, edge, poi, zone 편집 결과를 draft에 저장하고 최신 floor 데이터를 반환합니다.")
    @PutMapping("/buildings/{buildingId}/floors/{floorId}/draft")
    public ApiResponse<MapEditorInitResponseDTO> saveFloorDraft(
            @PathVariable UUID buildingId,
            @PathVariable UUID floorId,
            @RequestParam UUID tenantId,
            @RequestBody MapEditorDraftSaveRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        validateTenantAccess(userDetails, tenantId);
        return ApiResponse.success(
                GeneralSuccessCode.OK,
                mapEditorService.saveFloorDraft(
                        tenantId,
                        buildingId,
                        floorId,
                        userDetails != null ? userDetails.getUserId() : null,
                        request
                )
        );
    }

    private void validateTenantAccess(CustomUserDetails userDetails, UUID tenantId) {
        if (userDetails == null || !tenantQueryFacade.isUserMemberOfTenant(userDetails.getUserId(), tenantId)) {
            throw new AiException(AiErrorCode.AI_TENANT_NOT_FOUND);
        }
    }

    @Operation(summary = "수직 이동수단 목록 조회", description = "건물의 draft 맵 버전에 등록된 모든 수직 이동수단(엘리베이터, 계단 등)과 연결 노드를 조회합니다.")
    @GetMapping("/buildings/{buildingId}/vertical-connectors")
    public ApiResponse<List<MapEditorVerticalConnectorDTO>> getVerticalConnectors(
            @PathVariable UUID buildingId,
            @RequestParam UUID tenantId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        validateTenantAccess(userDetails, tenantId);
        return ApiResponse.success(
                GeneralSuccessCode.OK,
                mapEditorService.getVerticalConnectors(
                        tenantId,
                        buildingId,
                        userDetails != null ? userDetails.getUserId() : null
                )
        );
    }

    @Operation(summary = "수직 이동수단 생성", description = "신규 수직 이동수단(엘리베이터, 계단 등)을 생성합니다.")
    @PostMapping("/buildings/{buildingId}/vertical-connectors")
    public ApiResponse<MapEditorVerticalConnectorDTO> createVerticalConnector(
            @PathVariable UUID buildingId,
            @RequestParam UUID tenantId,
            @RequestBody MapEditorVerticalConnectorCreateRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        validateTenantAccess(userDetails, tenantId);
        return ApiResponse.success(
                GeneralSuccessCode.OK,
                mapEditorService.createVerticalConnector(
                        tenantId,
                        buildingId,
                        userDetails != null ? userDetails.getUserId() : null,
                        request
                )
        );
    }

    @Operation(summary = "수직 이동수단 삭제", description = "지정한 수직 이동수단을 삭제하고 연관된 노드 매핑 관계를 모두 해제합니다.")
    @DeleteMapping("/buildings/{buildingId}/vertical-connectors/{connectorId}")
    public ApiResponse<Void> deleteVerticalConnector(
            @PathVariable UUID buildingId,
            @PathVariable UUID connectorId,
            @RequestParam UUID tenantId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        validateTenantAccess(userDetails, tenantId);
        mapEditorService.deleteVerticalConnector(
                tenantId,
                buildingId,
                connectorId,
                userDetails != null ? userDetails.getUserId() : null
        );
        return ApiResponse.success(GeneralSuccessCode.OK, null);
    }

    @Operation(summary = "수직 이동수단 정보 수정", description = "수직 이동수단의 명칭, 종류, 대기 시간(가중치), 방향 속성을 수정합니다.")
    @PatchMapping("/buildings/{buildingId}/vertical-connectors/{connectorId}")
    public ApiResponse<MapEditorVerticalConnectorDTO> updateVerticalConnector(
            @PathVariable UUID buildingId,
            @PathVariable UUID connectorId,
            @RequestParam UUID tenantId,
            @RequestBody MapEditorVerticalConnectorUpdateRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        validateTenantAccess(userDetails, tenantId);
        return ApiResponse.success(
                GeneralSuccessCode.OK,
                mapEditorService.updateVerticalConnector(
                        tenantId,
                        buildingId,
                        connectorId,
                        userDetails != null ? userDetails.getUserId() : null,
                        request
                )
        );
    }

    @Operation(summary = "수직 이동수단 노드 연결", description = "특정 층의 노드를 지정한 수직 이동수단에 연결(매핑)합니다.")
    @PostMapping("/buildings/{buildingId}/vertical-connectors/{connectorId}/nodes")
    public ApiResponse<MapEditorVerticalConnectorDTO> mapVerticalConnectorNode(
            @PathVariable UUID buildingId,
            @PathVariable UUID connectorId,
            @RequestParam UUID tenantId,
            @RequestBody MapEditorVerticalConnectorMapRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        validateTenantAccess(userDetails, tenantId);
        return ApiResponse.success(
                GeneralSuccessCode.OK,
                mapEditorService.mapVerticalConnectorNode(
                        tenantId,
                        buildingId,
                        connectorId,
                        userDetails != null ? userDetails.getUserId() : null,
                        request
                )
        );
    }

    @Operation(summary = "수직 이동수단 노드 해제", description = "특정 층의 노드와 수직 이동수단 간의 연결(매핑)을 해제합니다.")
    @DeleteMapping("/buildings/{buildingId}/vertical-connectors/{connectorId}/floors/{floorId}")
    public ApiResponse<MapEditorVerticalConnectorDTO> unmapVerticalConnectorNode(
            @PathVariable UUID buildingId,
            @PathVariable UUID connectorId,
            @PathVariable UUID floorId,
            @RequestParam UUID tenantId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        validateTenantAccess(userDetails, tenantId);
        return ApiResponse.success(
                GeneralSuccessCode.OK,
                mapEditorService.unmapVerticalConnectorNode(
                        tenantId,
                        buildingId,
                        connectorId,
                        floorId,
                        userDetails != null ? userDetails.getUserId() : null
                )
        );
    }

    @Operation(summary = "맵 드래프트 최종 배포", description = "건물의 현재 draft 버전을 published 상태로 변환하여 최종 배포합니다.")
    @PostMapping("/buildings/{buildingId}/publish")
    public ApiResponse<MapEditorInitBuildingDraftResponseDTO> publishBuildingDraft(
            @PathVariable UUID buildingId,
            @RequestParam UUID tenantId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        validateTenantAccess(userDetails, tenantId);
        return ApiResponse.success(
                GeneralSuccessCode.OK,
                mapEditorService.publishBuildingDraft(
                        tenantId,
                        buildingId,
                        userDetails != null ? userDetails.getUserId() : null
                )
        );
    }

    @Operation(summary = "건물 내 draft POI 전체 조회", description = "최종 배포 전 카카오맵 매핑 및 검증을 위해 건물의 모든 draft POI 목록을 가져옵니다.")
    @GetMapping("/buildings/{buildingId}/draft-pois")
    public ApiResponse<List<MapEditorDraftPoiResponseDTO>> getBuildingDraftPois(
            @PathVariable UUID buildingId,
            @RequestParam UUID tenantId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        validateTenantAccess(userDetails, tenantId);
        return ApiResponse.success(
                GeneralSuccessCode.OK,
                mapEditorService.getBuildingDraftPois(tenantId, buildingId)
        );
    }

    @Operation(summary = "POI 외부 API ID 및 위경도 매핑 저장", description = "관리자가 승인/매핑한 POI들의 externalApiId 및 위경도 정보를 draft 상태의 POI에 업데이트합니다.")
    @PutMapping("/buildings/{buildingId}/poi-mappings")
    public ApiResponse<Void> saveBuildingPoiMappings(
            @PathVariable UUID buildingId,
            @RequestParam UUID tenantId,
            @RequestBody MapEditorPoiMappingsSaveRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        validateTenantAccess(userDetails, tenantId);
        mapEditorService.saveBuildingPoiMappings(tenantId, buildingId, request);
        return ApiResponse.success(GeneralSuccessCode.OK, null);
    }
}
