package com.insideout.backend.global.infra.storage.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * S3 관련 빈 등록.
 *
 * S3Client: 서버가 직접 파일 업로드/다운로드/삭제할 때 사용
 * S3Presigner: 클라이언트(웹/앱)가 직접 S3에 접근하도록 임시 URL 발급할 때 사용
 *   - 관리자 맵 에디터가 도면 표시할 때 활용
 *   - 사용 후 자동 만료되어 보안적
 */
@Configuration
@EnableConfigurationProperties(S3Properties.class)
public class S3Config {

    @Bean
    public S3Client s3Client(S3Properties props) {
        return S3Client.builder()
                .endpointOverride(URI.create(props.endpoint()))
                .region(Region.of(props.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                props.accessKey(),
                                props.secretKey()
                        )
                ))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(props.pathStyleAccess())
                        .build())
                .build();
    }

    /**
     * S3Presigner 빈.
     *
     * 사용 예: 관리자가 맵 에디터에서 도면 이미지를 볼 때,
     * 백엔드가 10분 유효한 presigned URL을 발급하면
     * 브라우저가 직접 S3에서 다운로드.
     */
    @Bean
    public S3Presigner s3Presigner(S3Properties props) {
        String presignerEndpoint = (props.publicEndpoint() == null || props.publicEndpoint().isBlank())
                ? props.endpoint()
                : props.publicEndpoint();

        return S3Presigner.builder()
                .endpointOverride(URI.create(presignerEndpoint))
                .region(Region.of(props.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                props.accessKey(),
                                props.secretKey()
                        )
                ))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(props.pathStyleAccess())
                        .build())
                .build();
    }
}
