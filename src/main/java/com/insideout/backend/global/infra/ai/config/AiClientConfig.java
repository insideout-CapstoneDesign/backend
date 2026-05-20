package com.insideout.backend.global.infra.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiClientConfig {

    @Bean
    public WebClient aiWebClient(AiProperties props){
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(props.timeoutSeconds()));

        return WebClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
