package com.insideout.backend.domain.map.storage;

import com.insideout.backend.domain.map.entity.MapType;

import java.util.UUID;

public record MapAssetDescriptor(
        MapType mapType,
        UUID ownerId,
        String bucketName,
        String objectKey,
        String imageUrl,
        int widthPx,
        int heightPx
) {
}
