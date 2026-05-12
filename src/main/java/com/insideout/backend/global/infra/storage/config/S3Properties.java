package com.insideout.backend.global.infra.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml의 storage.s3.* 값을 자동으로 바인딩한다.
 *
 * Spring Boot가 시작될 때:
 * 1. storage.s3.endpoint → endpoint
 * 2. storage.s3.access-key → accessKey (kebab-case → camelCase 자동)
 * 등등 모든 값을 이 record에 채워준다.
 *
 * 사용 예:
 *   @Autowired
 *   private S3Properties s3Properties;
 *
 *   s3Properties.bucket();  // "insideout-floorplan"
 */
@ConfigurationProperties(prefix = "storage.s3")
public record S3Properties(
        String endpoint,
        String region,
        String accessKey,
        String secretKey,
        String bucket,
        boolean pathStyleAccess
) {
}