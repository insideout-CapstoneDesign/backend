package com.insideout.backend.domain.map.controller;

import com.insideout.backend.domain.map.dto.response.PublishedMapFloorResponseDTO;
import com.insideout.backend.domain.map.service.PublishedMapService;
import com.insideout.backend.global.apiPayload.ApiResponse;
import com.insideout.backend.global.apiPayload.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Published Map", description = "사용자 앱에서 배포된 실내 지도 벡터 데이터를 조회하는 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/maps")
public class PublishedMapController {

    private final PublishedMapService publishedMapService;

    @Operation(summary = "배포된 층별 실내 지도 조회", description = "published 맵 버전의 벽, 구역, POI, 노드/엣지 데이터를 반환합니다.")
    @GetMapping("/floors/{floorId}")
    public ApiResponse<PublishedMapFloorResponseDTO> getPublishedFloorMap(
            @PathVariable UUID floorId
    ) {
        return ApiResponse.success(
                GeneralSuccessCode.OK,
                publishedMapService.getPublishedFloorMap(floorId)
        );
    }
}
