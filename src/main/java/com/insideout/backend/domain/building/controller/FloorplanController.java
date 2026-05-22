package com.insideout.backend.domain.building.controller;

import com.insideout.backend.domain.building.dto.response.FloorplanResponseDTO;
import com.insideout.backend.domain.building.service.FloorplanService;
import com.insideout.backend.global.apiPayload.ApiResponse;
import com.insideout.backend.global.apiPayload.code.GeneralSuccessCode;
import com.insideout.backend.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Tag(name = "Floorplan", description = "도면 관리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/buildings")
public class FloorplanController {

    private final FloorplanService floorplanService;

    @Operation(summary = "층 도면 등록", description = "특정 건물의 특정 층에 실내 도면 이미지를 업로드합니다.")
    @PostMapping(value = "/{buildingId}/floors/{floorId}/floorplans", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FloorplanResponseDTO> uploadFloorplan(
            @RequestParam UUID tenantId,
            @PathVariable UUID buildingId,
            @PathVariable UUID floorId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ApiResponse.success(
                GeneralSuccessCode.CREATED,
                floorplanService.uploadFloorplan(tenantId, buildingId, floorId, file, userDetails)
        );
    }
}
