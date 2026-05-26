package com.insideout.backend.domain.place.controller;

import com.insideout.backend.domain.place.dto.response.PlaceNearestResponse;
import com.insideout.backend.domain.place.dto.response.PlaceSearchItemResponse;
import com.insideout.backend.domain.place.exception.PlaceSuccessCode;
import com.insideout.backend.domain.place.service.PlaceSearchService;
import com.insideout.backend.global.apiPayload.ApiResponse;
import com.insideout.backend.global.apiPayload.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Place", description = "장소 검색/조회 API")
@RestController
@RequestMapping("/api/v1/places")
@RequiredArgsConstructor
public class PlaceSearchController {

    private final PlaceSearchService placeSearchService;

    @Operation(summary = "장소 키워드 검색", description = "키워드로 장소를 검색합니다.")
    @GetMapping("/search")
    public ApiResponse<List<PlaceSearchItemResponse>> search(
            @RequestParam("q") String query,
            @RequestParam(value = "lat", required = false) Double lat,
            @RequestParam(value = "lng", required = false) Double lng,
            @RequestParam(value = "radius", required = false) Integer radius
    ) {
        return ApiResponse.success(GeneralSuccessCode.OK, placeSearchService.search(query, lat, lng, radius));
    }

    @Operation(
            summary = "지도 클릭 기반 주변 장소 1건 조회",
            description = "클릭 좌표(lat/lng)를 기준으로 가장 가까운 장소 1건을 조회합니다."
    )
    @GetMapping("/nearest")
    public ApiResponse<PlaceNearestResponse> nearest(
            @RequestParam("lat") Double lat,
            @RequestParam("lng") Double lng,
            @RequestParam(value = "radius", required = false) Integer radius
    ) {
        return placeSearchService.findNearest(lat, lng, radius)
                .map(item -> ApiResponse.success(GeneralSuccessCode.OK, item))
                .orElseGet(() -> ApiResponse.success(PlaceSuccessCode.PLACE_INFO_NOT_AVAILABLE, null));
    }
}
