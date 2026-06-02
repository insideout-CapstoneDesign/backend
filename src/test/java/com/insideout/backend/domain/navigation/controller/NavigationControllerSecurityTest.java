package com.insideout.backend.domain.navigation.controller;

import com.insideout.backend.domain.navigation.dto.NavigationResponseDto;
import com.insideout.backend.domain.navigation.service.NavigationService;
import com.insideout.backend.global.security.CustomUserDetailsService;
import com.insideout.backend.global.security.SecurityConfig;
import com.insideout.backend.global.security.jwt.JwtAccessDeniedHandler;
import com.insideout.backend.global.security.jwt.JwtAuthenticationEntryPoint;
import com.insideout.backend.global.security.jwt.JwtAuthenticationFilter;
import com.insideout.backend.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NavigationController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class
})
class NavigationControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NavigationService navigationService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void findRoutes_allowsAnonymousRequest() throws Exception {
        when(navigationService.findRoutes(any()))
                .thenReturn(new NavigationResponseDto(
                        new NavigationResponseDto.CoordinateDto(126.95, 37.47, "도착"),
                        new NavigationResponseDto.CoordinateDto(126.95, 37.47, "도착"),
                        null,
                        List.of(),
                        List.of(),
                        null
                ));

        mockMvc.perform(post("/api/v1/navigation/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startX": 126.951744,
                                  "startY": 37.478095,
                                  "endX": 126.95,
                                  "endY": 37.47,
                                  "includeIndoor": true,
                                  "routeTypes": ["WALK"]
                                }
                                """))
                .andExpect(status().isOk());

        verify(navigationService).findRoutes(any());
    }
}
