package com.insideout.backend.domain.navigation.controller;

import com.insideout.backend.domain.navigation.dto.NavigationRequestDto;
import com.insideout.backend.domain.navigation.dto.NavigationResponseDto;
import com.insideout.backend.domain.navigation.service.NavigationService;
import com.insideout.backend.global.apiPayload.ApiResponse;
import com.insideout.backend.global.apiPayload.code.GeneralSuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/navigation")
@RequiredArgsConstructor
public class NavigationController {

    private final NavigationService navigationService;

    @PostMapping("/routes")
    public ApiResponse<NavigationResponseDto> findRoutes(
            @Valid @RequestBody NavigationRequestDto request
    ) {
        return ApiResponse.success(GeneralSuccessCode.OK, navigationService.findRoutes(request));
    }

    @PostMapping("/transit/routes")
    public ApiResponse<NavigationResponseDto> findTransitRoute(
            @Valid @RequestBody NavigationRequestDto request
    ) {
        return ApiResponse.success(GeneralSuccessCode.OK, navigationService.findTransitRoutes(request));
    }
}
