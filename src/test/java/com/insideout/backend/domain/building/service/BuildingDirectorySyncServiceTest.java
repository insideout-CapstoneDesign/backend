package com.insideout.backend.domain.building.service;

import com.insideout.backend.domain.building.entity.Building;
import com.insideout.backend.domain.building.entity.BuildingDirectory;
import com.insideout.backend.domain.building.entity.Campus;
import com.insideout.backend.domain.building.repository.BuildingDirectoryRepository;
import com.insideout.backend.domain.building.repository.BuildingRepository;
import com.insideout.backend.domain.map.entity.MapVersion;
import com.insideout.backend.domain.map.enums.MapType;
import com.insideout.backend.domain.map.repository.MapVersionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BuildingDirectorySyncServiceTest {

    private static final UUID BUILDING_ID = UUID.fromString("75c55ba4-de5b-4144-aa2c-cacdbfd2ac82");
    private static final UUID TENANT_ID = UUID.fromString("feb333d1-b13a-4b08-a7f9-fc5a5621939a");
    private static final UUID MAP_VERSION_ID = UUID.fromString("9a7c2a10-0001-4000-8000-000000000001");

    @Mock
    private BuildingRepository buildingRepository;

    @Mock
    private BuildingDirectoryRepository buildingDirectoryRepository;

    @Mock
    private MapVersionRepository mapVersionRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private BuildingDirectorySyncService buildingDirectorySyncService;

    @org.junit.jupiter.api.BeforeEach
    void setUpTransactionTemplate() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @Test
    void sync_createsDirectoryFromBuildingAndPublishedVersion() {
        Building building = building();
        MapVersion publishedVersion = mapVersion();

        when(mapVersionRepository.findFirstByBuildingIdAndMapTypeAndStatusOrderByCreatedAtDesc(
                BUILDING_ID,
                MapType.BUILDING,
                "published"
        )).thenReturn(Optional.of(publishedVersion));
        when(buildingDirectoryRepository.save(any(BuildingDirectory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BuildingDirectory result = buildingDirectorySyncService.sync(building);

        ArgumentCaptor<BuildingDirectory> captor = ArgumentCaptor.forClass(BuildingDirectory.class);
        verify(buildingDirectoryRepository).save(captor.capture());

        BuildingDirectory saved = captor.getValue();
        assertThat(result).isSameAs(saved);
        assertThat(saved.getId()).isEqualTo(BUILDING_ID);
        assertThat(saved.getName()).isEqualTo("신세계백화점 본점 디 에스테이트");
        assertThat(saved.getAddress()).isEqualTo("서울특별시 중구 퇴계로 77");
        assertThat(saved.isPublic()).isTrue();
        assertThat(saved.getPublishedVersion()).isEqualTo(publishedVersion);
        assertThat(saved.getCentroid()).isNotNull();
        assertThat(saved.getBbox()).isNotNull();
    }

    @Test
    void sync_withoutPublishedVersion_keepsDirectoryPrivate() {
        Building building = building();

        when(mapVersionRepository.findFirstByBuildingIdAndMapTypeAndStatusOrderByCreatedAtDesc(
                BUILDING_ID,
                MapType.BUILDING,
                "published"
        )).thenReturn(Optional.empty());
        when(buildingDirectoryRepository.save(any(BuildingDirectory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BuildingDirectory saved = buildingDirectorySyncService.sync(building);

        assertThat(saved.isPublic()).isFalse();
        assertThat(saved.getPublishedVersion()).isNull();
    }

    @Test
    void sync_withoutFootprint_usesMetaLocationAsFallbackGeometry() {
        Building building = building();
        when(building.getFootprint()).thenReturn(null);
        when(buildingDirectoryRepository.findByIdAndTenant_Id(BUILDING_ID, TENANT_ID)).thenReturn(Optional.empty());

        MapVersion publishedVersion = mapVersion();
        when(mapVersionRepository.findFirstByBuildingIdAndMapTypeAndStatusOrderByCreatedAtDesc(
                BUILDING_ID,
                MapType.BUILDING,
                "published"
        )).thenReturn(Optional.of(publishedVersion));
        when(buildingDirectoryRepository.save(any(BuildingDirectory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BuildingDirectory saved = buildingDirectorySyncService.sync(building);

        assertThat(saved.getCentroid()).isNotNull();
        assertThat(saved.getBbox()).isNotNull();
        assertThat(saved.isPublic()).isTrue();
    }

    @Test
    void sync_withoutFootprint_preservesExistingDirectoryGeometry() {
        Building building = building();
        when(building.getFootprint()).thenReturn(null);

        BuildingDirectory existingDirectory = BuildingDirectory.builder()
                .id(BUILDING_ID)
                .tenant(building.getTenant())
                .campus(building.getCampus())
                .name("기존 이름")
                .address("기존 주소")
                .category("existing")
                .centroid(geometryFactory().createPoint(new Coordinate(126.98080, 37.56018)))
                .bbox(geometryFactory().createPolygon(new Coordinate[]{
                        new Coordinate(126.98070, 37.56010),
                        new Coordinate(126.98095, 37.56010),
                        new Coordinate(126.98095, 37.56025),
                        new Coordinate(126.98070, 37.56025),
                        new Coordinate(126.98070, 37.56010)
                }))
                .isPublic(true)
                .build();

        when(buildingDirectoryRepository.findByIdAndTenant_Id(BUILDING_ID, TENANT_ID)).thenReturn(Optional.of(existingDirectory));
        MapVersion publishedVersion = mapVersion();
        when(mapVersionRepository.findFirstByBuildingIdAndMapTypeAndStatusOrderByCreatedAtDesc(
                BUILDING_ID,
                MapType.BUILDING,
                "published"
        )).thenReturn(Optional.of(publishedVersion));
        when(buildingDirectoryRepository.save(any(BuildingDirectory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BuildingDirectory saved = buildingDirectorySyncService.sync(building);

        assertThat(saved.getCentroid()).isNotNull();
        assertThat(saved.getCentroid().getX()).isEqualTo(existingDirectory.getCentroid().getX());
        assertThat(saved.getCentroid().getY()).isEqualTo(existingDirectory.getCentroid().getY());
        assertThat(saved.getBbox()).isNotNull();
        assertThat(saved.getBbox().getEnvelopeInternal().getMinX()).isEqualTo(existingDirectory.getBbox().getEnvelopeInternal().getMinX());
        assertThat(saved.getBbox().getEnvelopeInternal().getMaxX()).isEqualTo(existingDirectory.getBbox().getEnvelopeInternal().getMaxX());
    }

    @Test
    void syncAllExisting_backfillsEveryBuilding() {
        Building building = building();
        when(buildingRepository.findAll(org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(building)));
        when(mapVersionRepository.findFirstByBuildingIdAndMapTypeAndStatusOrderByCreatedAtDesc(
                BUILDING_ID,
                MapType.BUILDING,
                "published"
        )).thenReturn(Optional.empty());
        when(buildingDirectoryRepository.save(any(BuildingDirectory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int syncedCount = buildingDirectorySyncService.syncAllExisting(100);

        assertThat(syncedCount).isEqualTo(1);
    }

    private Building building() {
        Building building = mock(Building.class);
        Campus campus = mock(Campus.class);
        com.insideout.backend.domain.tenant.entity.Tenant tenant = mock(com.insideout.backend.domain.tenant.entity.Tenant.class);

        when(building.getId()).thenReturn(BUILDING_ID);
        when(building.getTenant()).thenReturn(tenant);
        when(building.getName()).thenReturn("신세계백화점 본점 디 에스테이트");
        when(building.getAddress()).thenReturn("서울특별시 중구 퇴계로 77");
        when(building.getCampus()).thenReturn(campus);
        when(building.getFootprint()).thenReturn(geometryFactory().createPolygon(new Coordinate[]{
                new Coordinate(126.98074, 37.56010),
                new Coordinate(126.98090, 37.56010),
                new Coordinate(126.98090, 37.56024),
                new Coordinate(126.98074, 37.56024),
                new Coordinate(126.98074, 37.56010)
        }));
        when(building.getMeta()).thenReturn(Map.of(
                "category", "department_store_annex",
                "location", Map.of(
                        "latitude", "37.560170716582256",
                        "longitude", "126.98082156387552"
                )
        ));
        when(tenant.getId()).thenReturn(TENANT_ID);
        when(campus.getId()).thenReturn(UUID.fromString("616eabe0-42e3-48d9-9a2d-d96671fc1ff3"));
        when(campus.getTenant()).thenReturn(tenant);
        return building;
    }

    private MapVersion mapVersion() {
        MapVersion mapVersion = mock(MapVersion.class);
        when(mapVersion.getId()).thenReturn(MAP_VERSION_ID);
        return mapVersion;
    }

    private GeometryFactory geometryFactory() {
        return new GeometryFactory(new PrecisionModel(), 4326);
    }
}
