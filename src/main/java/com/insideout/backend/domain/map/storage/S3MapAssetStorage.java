package com.insideout.backend.domain.map.storage;

import com.insideout.backend.domain.building.entity.CampusMap;
import com.insideout.backend.domain.building.entity.Floorplan;
import com.insideout.backend.domain.building.repository.CampusMapRepository;
import com.insideout.backend.domain.building.repository.FloorplanRepository;
import com.insideout.backend.domain.map.enums.MapType;
import com.insideout.backend.global.infra.storage.service.S3StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3MapAssetStorage implements MapAssetStorage {

    private static final Duration MAP_IMAGE_URL_EXPIRY = Duration.ofMinutes(10);

    private final FloorplanRepository floorplanRepository;
    private final CampusMapRepository campusMapRepository;
    private final S3StorageService s3StorageService;

    @Override
    public Optional<MapAssetDescriptor> findCurrentMapAsset(MapType mapType, UUID ownerId) {
        if (ownerId == null) {
            return Optional.empty();
        }

        return switch (mapType) {
            case CAMPUS -> findCurrentCampusMap(ownerId);
            case BUILDING -> findCurrentFloorplan(ownerId);
        };
    }

    private Optional<MapAssetDescriptor> findCurrentCampusMap(UUID campusId) {
        return campusMapRepository.findByCampusIdAndIsCurrentTrue(campusId)
                .map(campusMap -> {
                    StoredObject storedObject = resolveStoredObject(
                            campusMap.getBucketName(),
                            campusMap.getObjectKey(),
                            campusMap.getImageUrl()
                    );
                    String imageUrl = presignOrFallback(storedObject, campusMap.getImageUrl());
                    return new MapAssetDescriptor(
                            MapType.CAMPUS,
                            campusId,
                            storedObject.bucket(),
                            storedObject.objectKey(),
                            imageUrl,
                            campusMap.getWidthPx(),
                            campusMap.getHeightPx()
                    );
                });
    }

    private Optional<MapAssetDescriptor> findCurrentFloorplan(UUID floorId) {
        return floorplanRepository.findByFloorIdAndIsCurrentTrue(floorId)
                .map(floorplan -> {
                    StoredObject storedObject = parseStoredObject(floorplan.getImageUrl());
                    String imageUrl = presignOrFallback(storedObject, floorplan.getImageUrl());
                    return new MapAssetDescriptor(
                            MapType.BUILDING,
                            floorId,
                            storedObject.bucket(),
                            storedObject.objectKey(),
                            imageUrl,
                            floorplan.getWidthPx(),
                            floorplan.getHeightPx()
                    );
                });
    }

    private StoredObject resolveStoredObject(String bucket, String objectKey, String imageUrl) {
        if (!isBlank(objectKey)) {
            return new StoredObject(defaultIfBlank(bucket, s3StorageService.defaultBucket()), objectKey, true);
        }
        return parseStoredObject(imageUrl);
    }

    private String presignOrFallback(StoredObject storedObject, String fallbackUrl) {
        if (!storedObject.canPresign()) {
            return fallbackUrl;
        }
        return s3StorageService.generatePresignedDownloadUrl(
                storedObject.bucket(),
                storedObject.objectKey(),
                MAP_IMAGE_URL_EXPIRY
        );
    }

    private StoredObject parseStoredObject(String imageUrl) {
        if (isBlank(imageUrl)) {
            return StoredObject.notPresignable();
        }
        if (imageUrl.startsWith("s3://")) {
            try {
                URI uri = URI.create(imageUrl);
                String bucket = defaultIfBlank(uri.getHost(), s3StorageService.defaultBucket());
                String key = uri.getPath() == null ? null : stripLeadingSlash(uri.getPath());
                return new StoredObject(bucket, key, true);
            } catch (IllegalArgumentException e) {
                return StoredObject.notPresignable();
            }
        }
        if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
            return StoredObject.notPresignable();
        }
        return new StoredObject(s3StorageService.defaultBucket(), imageUrl, true);
    }

    private String stripLeadingSlash(String value) {
        return value.startsWith("/") ? value.substring(1) : value;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record StoredObject(
            String bucket,
            String objectKey,
            boolean presignable
    ) {
        private static StoredObject notPresignable() {
            return new StoredObject(null, null, false);
        }

        private boolean canPresign() {
            return presignable
                    && bucket != null
                    && !bucket.isBlank()
                    && objectKey != null
                    && !objectKey.isBlank();
        }
    }
}
