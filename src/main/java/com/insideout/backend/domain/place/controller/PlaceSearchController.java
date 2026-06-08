package com.insideout.backend.domain.place.controller;

import com.insideout.backend.domain.place.dto.response.PlaceNearestResponse;
import com.insideout.backend.domain.place.dto.response.PlaceDetailResponse;
import com.insideout.backend.domain.place.dto.response.PlaceSearchItemResponse;
import com.insideout.backend.domain.place.exception.PlaceSuccessCode;
import com.insideout.backend.domain.place.service.detail.PlaceDetailService;
import com.insideout.backend.domain.place.service.search.PlaceSearchService;
import com.insideout.backend.domain.place.service.suggest.PlaceSuggestService;
import com.insideout.backend.global.apiPayload.ApiResponse;
import com.insideout.backend.global.apiPayload.code.GeneralErrorCode;
import com.insideout.backend.global.apiPayload.code.GeneralSuccessCode;
import com.insideout.backend.global.apiPayload.exception.ProjectException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.util.List;

@Tag(name = "Place", description = "장소 검색/조회 API")
@RestController
@RequestMapping("/api/v1/places")
@RequiredArgsConstructor
public class PlaceSearchController {

    private final PlaceSearchService placeSearchService;
    private final PlaceSuggestService placeSuggestService;
    private final PlaceDetailService placeDetailService;

    @Operation(summary = "장소 키워드 검색", description = "키워드로 장소를 검색합니다.")
    @GetMapping("/search")
    public ApiResponse<List<PlaceSearchItemResponse>> search(
            @RequestParam("q") String query,
            @RequestParam(value = "lat", required = false) Double lat,
            @RequestParam(value = "lng", required = false) Double lng,
            @RequestParam(value = "radius", required = false) Integer radius,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return ApiResponse.success(GeneralSuccessCode.OK, placeSearchService.search(query, lat, lng, radius, size));
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

    @Operation(
            summary = "장소 상세 조회",
            description = "placeId 또는 externalApiId를 기준으로 건물 상세와 실내 정보를 조회합니다."
    )
    @GetMapping("/detail")
    public ApiResponse<PlaceDetailResponse> detail(
            @RequestParam(value = "placeId", required = false) String placeId,
            @RequestParam(value = "externalApiId", required = false) String externalApiId
    ) {
        if (!StringUtils.hasText(placeId) && !StringUtils.hasText(externalApiId)) {
            throw new ProjectException(GeneralErrorCode.BAD_REQUEST);
        }

        return placeDetailService.getDetail(placeId, externalApiId)
                .map(item -> ApiResponse.success(GeneralSuccessCode.OK, item))
                .orElseGet(() -> ApiResponse.success(PlaceSuccessCode.PLACE_INFO_NOT_AVAILABLE, null));
    }

    @Operation(summary = "장소 자동완성", description = "입력 중인 키워드에 대해 자동완성 후보를 조회합니다.")
    @GetMapping("/suggest")
    public ApiResponse<List<PlaceSearchItemResponse>> suggest(
            @RequestParam("q") String query,
            @RequestParam(value = "lat", required = false) Double lat,
            @RequestParam(value = "lng", required = false) Double lng,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return ApiResponse.success(GeneralSuccessCode.OK, placeSuggestService.suggest(query, lat, lng, size));
    }
}
