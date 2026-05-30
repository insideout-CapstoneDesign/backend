package com.insideout.backend.global.infra.storage.service;

import com.insideout.backend.global.infra.storage.config.S3Properties;
import com.insideout.backend.global.infra.storage.exception.StorageErrorCode;
import com.insideout.backend.global.infra.storage.exception.StorageException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

/**
 * S3/MinIO 스토리지 서비스.
 *
 * 파일 업로드/다운로드/삭제 책임만 가진다.
 * 어떤 키(경로)로 저장할지는 호출자가 결정 (예: FloorplanService).
 *
 * AI 서버는 boto3로 직접 인증 다운로드하므로, 여기서 presigned URL 생성은 불필요.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class S3StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;

    /**
     * MultipartFile을 S3에 업로드.
     *
     * @param file 업로드할 파일
     * @param key 저장 경로 (예: "tenants/abc/buildings/eng/floor-2.png")
     * @return 저장된 키 (그대로 반환)
     * @throws StorageException 업로드 실패 시
     */
    public String upload(MultipartFile file, String key) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(s3Properties.bucket())
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(
                    request,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize())
            );

            log.info("Uploaded to S3: bucket={}, key={}, size={}",
                    s3Properties.bucket(), key, file.getSize());
            return key;

        } catch (IOException | S3Exception e) {
            log.error("S3 upload failed: key={}", key, e);
            throw new StorageException(StorageErrorCode.UPLOAD_FAILED);
        }
    }

    /**
     * key로부터 AI 서버에 전달할 S3 URL 생성.
     *
     * @param key 저장된 키
     * @return "s3://bucket-name/key" 형식
     */
    public String buildS3Url(String key) {
        return String.format("s3://%s/%s", s3Properties.bucket(), key);
    }

    /**
     * 클라이언트(웹/앱)가 직접 다운로드 가능한 presigned URL 생성. 🆕
     *
     * 사용 예:
     *   String url = storage.generatePresignedDownloadUrl(key, Duration.ofMinutes(10));
     *   // 프론트에 url 전달 → <img src={url}/> 으로 표시 가능
     *
     * @param key 다운로드할 객체 키
     * @param expiry 유효 기간 (예: 10분)
     * @return 유효 기간 내에 다운로드 가능한 URL
     */
    public String generatePresignedDownloadUrl(String key, Duration expiry) {
        return generatePresignedDownloadUrl(s3Properties.bucket(), key, expiry);
    }

    public String generatePresignedDownloadUrl(String bucket, String key, Duration expiry) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(expiry)
                    .getObjectRequest(getObjectRequest)
                    .build();

            String url = s3Presigner.presignGetObject(presignRequest).url().toString();
            log.debug("Generated presigned URL for bucket={}, key={}, expiry={}s", bucket, key, expiry.getSeconds());
            return url;
        } catch (Exception e) {
            log.error("Failed to generate presigned URL: bucket={}, key={}", bucket, key, e);
            throw new StorageException(StorageErrorCode.DOWNLOAD_FAILED);
        }
    }

    public String defaultBucket() {
        return s3Properties.bucket();
    }

    /**
     * s3://bucket-name/key 형식의 URL을 클라이언트 다운로드 가능한 presigned URL로 변환.
     * 유효기간은 60분으로 지정합니다.
     */
    public String getPresignedUrlFromS3Url(String s3Url) {
        if (s3Url == null || !s3Url.startsWith("s3://")) {
            return s3Url;
        }

        try {
            String bucketPrefix = "s3://";
            int bucketEnd = s3Url.indexOf("/", bucketPrefix.length());
            if (bucketEnd == -1) {
                return s3Url;
            }
            String bucket = s3Url.substring(bucketPrefix.length(), bucketEnd);
            String key = s3Url.substring(bucketEnd + 1);

            return generatePresignedDownloadUrl(bucket, key, Duration.ofMinutes(60));
        } catch (Exception e) {
            log.error("Failed to parse S3 URL to presigned URL: {}", s3Url, e);
            return s3Url;
        }
    }


    /**
     * 파일 삭제.
     */
    public void delete(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(s3Properties.bucket())
                    .key(key)
                    .build());

            log.info("Deleted from S3: key={}", key);
        } catch (S3Exception e) {
            log.error("S3 delete failed: key={}", key, e);
            throw new StorageException(StorageErrorCode.DELETE_FAILED);
        }
    }

    /**
     * 파일 존재 여부 확인.
     */
    public boolean exists(String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(s3Properties.bucket())
                    .key(key)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    /**
     * 도면 업로드 키를 표준 형식으로 생성하는 헬퍼.
     *
     * 형식: tenants/{tenantId}/buildings/{buildingId}/floors/{floorId}/{uuid}_{filename}
     * UUID 접두사로 같은 파일명 충돌 방지.
     */
    public String buildFloorplanKey(
            UUID tenantId,
            UUID buildingId,
            UUID floorId,
            String originalFilename
    ) {
        String safeName = sanitizeFilename(originalFilename);
        return String.format(
                "tenants/%s/buildings/%s/floors/%s/%s_%s",
                tenantId, buildingId, floorId,
                UUID.randomUUID(), safeName
        );
    }

    /**
     * 캠퍼스 야외 도면 업로드 키 생성 헬퍼.
     */
    public String buildCampusMapKey(
            UUID tenantId,
            UUID campusId,
            String originalFilename
    ) {
        String safeName = sanitizeFilename(originalFilename);
        return String.format(
                "tenants/%s/campuses/%s/maps/%s_%s",
                tenantId, campusId,
                UUID.randomUUID(), safeName
        );
    }

    private static final int MAX_FILENAME_LENGTH = 100;
    private static final String DEFAULT_FILENAME = "image.png";

    /**
     * 원본 파일명에서 basename 추출 후, 안전하지 않은 문자를 제거/치환하여 반환.
     * null·빈 문자열이면 기본값 "image.png"을 반환한다.
     */
    private String sanitizeFilename(String original) {
        if (original == null || original.isBlank()) {
            return DEFAULT_FILENAME;
        }

        // 1) 경로 구분자 통일 후 basename 추출
        String cleaned = original.replace("\\", "/");
        String basename = cleaned.substring(cleaned.lastIndexOf('/') + 1);

        // 2) 제어문자·비인쇄 문자 제거
        basename = basename.replaceAll("[\\p{Cntrl}\\p{Cc}]", "");

        // 3) 안전하지 않은 문자(공백·특수문자 등)를 언더스코어로 치환 (영문, 숫자, 점, 하이픈, 언더스코어만 허용)
        basename = basename.replaceAll("[^a-zA-Z0-9.\\-_]", "_");

        // 4) 길이 제한
        if (basename.length() > MAX_FILENAME_LENGTH) {
            basename = basename.substring(basename.length() - MAX_FILENAME_LENGTH);
        }

        return basename.isBlank() ? DEFAULT_FILENAME : basename;
    }
}
