package com.insideout.backend.global.infra.kakao.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(KakaoLocalProperties.class)
public class KakaoLocalConfig {

    @Bean
    public RestClient kakaoLocalRestClient(RestClient.Builder builder, KakaoLocalProperties properties) {
        return builder
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.restApiKey())
                .build();
    }
}
