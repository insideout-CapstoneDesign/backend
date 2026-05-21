package com.insideout.backend.global.infra.storage.service;

import com.insideout.backend.global.infra.storage.exception.StorageErrorCode;
import com.insideout.backend.global.infra.storage.exception.StorageException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImageStorageService {

    private final S3StorageService s3StorageService;
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    /**
     * 도면 이미지를 검증한 후 S3에 업로드하고, s3:// 형식을 따르는 S3 URL을 반환합니다.
     */
    public String uploadFloorplanImage(UUID tenantId, UUID buildingId, UUID floorId, MultipartFile file) {
        validateFile(file);

        String originalFilename = file.getOriginalFilename();
        String key = s3StorageService.buildFloorplanKey(tenantId, buildingId, floorId, originalFilename);
        
        s3StorageService.upload(file, key);
        return s3StorageService.buildS3Url(key);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new StorageException(StorageErrorCode.INVALID_FILE);
        }

        // 1. 용량 검증 (10MB 제한)
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new StorageException(StorageErrorCode.FILE_SIZE_EXCEEDED);
        }

        // 2. 확장자 검증 (.png, .jpg, .jpeg)
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new StorageException(StorageErrorCode.INVALID_FILE);
        }

        String lowerName = originalFilename.toLowerCase();
        if (!lowerName.endsWith(".png") && !lowerName.endsWith(".jpg") && !lowerName.endsWith(".jpeg")) {
            throw new StorageException(StorageErrorCode.INVALID_FILE_EXTENSION);
        }
    }
}
