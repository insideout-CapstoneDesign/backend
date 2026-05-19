package com.insideout.backend.domain.map.storage;

import com.insideout.backend.domain.map.entity.MapType;

import java.util.Optional;
import java.util.UUID;

/**
 * MinIO/S3에서 캠퍼스 도면과 건물 도면을 동일한 방식으로 조회하기 위한 포트.
 */
public interface MapAssetStorage {

    Optional<MapAssetDescriptor> findCurrentMapAsset(MapType mapType, UUID ownerId);
}
