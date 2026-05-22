package com.insideout.backend.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;
import java.time.Duration;

class RestTemplateConfigTest {

    private final RestTemplateConfig restTemplateConfig = new RestTemplateConfig();

    @Test
    void tmapRestTemplateHasConnectAndReadTimeouts() {
        RestTemplateBuilder customBuilder = new RestTemplateBuilder() {
            private Duration configuredConnectTimeout;
            private Duration configuredReadTimeout;

            @Override
            public RestTemplateBuilder connectTimeout(Duration connectTimeout) {
                this.configuredConnectTimeout = connectTimeout;
                return this;
            }

            @Override
            public RestTemplateBuilder readTimeout(Duration readTimeout) {
                this.configuredReadTimeout = readTimeout;
                return this;
            }

            @Override
            public RestTemplate build() {
                JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                        java.net.http.HttpClient.newBuilder().connectTimeout(configuredConnectTimeout).build()
                );
                factory.setReadTimeout(configuredReadTimeout);
                RestTemplate restTemplate = new RestTemplate();
                restTemplate.setRequestFactory(factory);
                return restTemplate;
            }
        };

        RestTemplate restTemplate = restTemplateConfig.tmapRestTemplate(customBuilder);

        assertThat(restTemplate.getRequestFactory()).isInstanceOf(JdkClientHttpRequestFactory.class);

        JdkClientHttpRequestFactory requestFactory =
                (JdkClientHttpRequestFactory) restTemplate.getRequestFactory();
        HttpClient httpClient = (HttpClient) ReflectionTestUtils.getField(requestFactory, "httpClient");
        assertThat(httpClient).isNotNull();
        assertThat(httpClient.connectTimeout()).contains(Duration.ofSeconds(3));
        assertThat(ReflectionTestUtils.getField(requestFactory, "readTimeout")).isEqualTo(Duration.ofSeconds(10));
    }
}
