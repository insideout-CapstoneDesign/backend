package com.insideout.backend.domain.map.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.insideout.backend.domain.building.entity.CampusMap;
import com.insideout.backend.domain.building.entity.Floorplan;
import com.insideout.backend.domain.building.repository.CampusMapRepository;
import com.insideout.backend.domain.building.repository.FloorplanRepository;
import com.insideout.backend.domain.map.enums.MapType;
import com.insideout.backend.global.infra.storage.service.S3StorageService;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class S3MapAssetStorageTest {

    @Mock
    private FloorplanRepository floorplanRepository;

    @Mock
    private CampusMapRepository campusMapRepository;

    @Mock
    private S3StorageService s3StorageService;

    private S3MapAssetStorage storage;

    @BeforeEach
    void setUp() {
        storage = new S3MapAssetStorage(floorplanRepository, campusMapRepository, s3StorageService);
    }

    @Test
    void campusMapPresignsS3ImageUrlWhenObjectKeyIsMissing() {
        UUID campusId = UUID.randomUUID();
        CampusMap campusMap = campusMap(null, null, "s3://campus-bucket/maps/campus.png");

        when(campusMapRepository.findByCampusIdAndIsCurrentTrue(campusId)).thenReturn(Optional.of(campusMap));
        when(s3StorageService.generatePresignedDownloadUrl(
                eq("campus-bucket"),
                eq("maps/campus.png"),
                any(Duration.class)
        )).thenReturn("https://signed.example/campus.png");

        Optional<MapAssetDescriptor> result = storage.findCurrentMapAsset(MapType.CAMPUS, campusId);

        assertThat(result).isPresent();
        assertThat(result.get().bucketName()).isEqualTo("campus-bucket");
        assertThat(result.get().objectKey()).isEqualTo("maps/campus.png");
        assertThat(result.get().imageUrl()).isEqualTo("https://signed.example/campus.png");
    }

    @Test
    void floorplanFallsBackToOriginalImageUrlWhenS3UrlIsMalformed() {
        UUID floorId = UUID.randomUUID();
        String malformedUrl = "s3://bad bucket/maps/floor.png";
        Floorplan floorplan = floorplan(malformedUrl);

        when(floorplanRepository.findByFloorIdAndIsCurrentTrue(floorId)).thenReturn(Optional.of(floorplan));

        Optional<MapAssetDescriptor> result = storage.findCurrentMapAsset(MapType.BUILDING, floorId);

        assertThat(result).isPresent();
        assertThat(result.get().bucketName()).isNull();
        assertThat(result.get().objectKey()).isNull();
        assertThat(result.get().imageUrl()).isEqualTo(malformedUrl);
        verify(s3StorageService, never()).generatePresignedDownloadUrl(any(), any(), any());
    }

    private CampusMap campusMap(String bucketName, String objectKey, String imageUrl) {
        CampusMap campusMap = org.mockito.Mockito.mock(CampusMap.class);
        when(campusMap.getBucketName()).thenReturn(bucketName);
        when(campusMap.getObjectKey()).thenReturn(objectKey);
        when(campusMap.getImageUrl()).thenReturn(imageUrl);
        when(campusMap.getWidthPx()).thenReturn(100);
        when(campusMap.getHeightPx()).thenReturn(100);
        return campusMap;
    }

    private Floorplan floorplan(String imageUrl) {
        Floorplan floorplan = org.mockito.Mockito.mock(Floorplan.class);
        when(floorplan.getImageUrl()).thenReturn(imageUrl);
        when(floorplan.getWidthPx()).thenReturn(100);
        when(floorplan.getHeightPx()).thenReturn(100);
        return floorplan;
    }
}
