package com.insideout.backend.global.infra.storage.service;

import com.insideout.backend.global.apiPayload.ApiResponse;
import com.insideout.backend.global.apiPayload.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * S3StorageService 테스트용 임시 컨트롤러.
 *
 * ⚠️ 실제 floorplan/AI 도메인 컨트롤러 만든 후엔 삭제 예정.
 */
@Tag(name = "Storage Test", description = "S3 스토리지 동작 검증용 (임시)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/test/storage")
public class StorageTestController {

    private final S3StorageService storage;

    @Operation(summary = "파일 업로드", description = "MultipartFile을 S3에 업로드하고 키와 URL 반환")
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ApiResponse<Map<String, String>> upload(@RequestPart("file") MultipartFile file) {
        String key = storage.buildFloorplanKey(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                file.getOriginalFilename()
        );
        storage.upload(file, key);
        return ApiResponse.success(GeneralSuccessCode.OK, Map.of(
                "key", key,
                "s3Url", storage.buildS3Url(key)
        ));
    }

    @Operation(summary = "Presigned URL 발급", description = "10분 동안 유효한 다운로드 URL 생성")
    @GetMapping("/presigned-url")
    public ApiResponse<Map<String, String>> getPresignedUrl(@RequestParam("key") String key) {
        String url = storage.generatePresignedDownloadUrl(key, Duration.ofMinutes(10));
        return ApiResponse.success(GeneralSuccessCode.OK, Map.of(
                "presignedUrl", url
        ));
    }

    @Operation(summary = "파일 존재 확인")
    @GetMapping("/exists")
    public ApiResponse<Map<String, Boolean>> exists(@RequestParam("key") String key) {
        boolean exists = storage.exists(key);
        return ApiResponse.success(GeneralSuccessCode.OK, Map.of("exists", exists));
    }

    @Operation(summary = "파일 삭제")
    @DeleteMapping
    public ApiResponse<String> delete(@RequestParam("key") String key) {
        storage.delete(key);
        return ApiResponse.success(GeneralSuccessCode.OK, "deleted");
    }
}