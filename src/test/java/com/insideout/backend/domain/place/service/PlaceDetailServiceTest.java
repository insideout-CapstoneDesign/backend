package com.insideout.backend.domain.place.service;

import com.insideout.backend.domain.building.entity.Building;
import com.insideout.backend.domain.building.entity.BuildingDirectory;
import com.insideout.backend.domain.building.entity.Floor;
import com.insideout.backend.domain.building.entity.Floorplan;
import com.insideout.backend.domain.building.repository.BuildingDirectoryRepository;
import com.insideout.backend.domain.building.repository.BuildingRepository;
import com.insideout.backend.domain.building.repository.FloorRepository;
import com.insideout.backend.domain.building.repository.FloorplanRepository;
import com.insideout.backend.domain.map.entity.MapVersion;
import com.insideout.backend.domain.map.entity.PoiCategory;
import com.insideout.backend.domain.map.entity.Poi;
import com.insideout.backend.domain.map.repository.PoiCategoryRepository;
import com.insideout.backend.domain.map.repository.MapVersionRepository;
import com.insideout.backend.domain.map.repository.PoiRepository;
import com.insideout.backend.domain.place.dto.response.PlaceDetailResponse;
import com.insideout.backend.domain.place.service.detail.PlaceDetailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class PlaceDetailServiceTest {

    private static final UUID BUILDING_ID = UUID.fromString("7fb64c7b-9055-4c01-bdc9-c96fd526e7d8");
    private static final UUID MAP_VERSION_ID = UUID.fromString("9a7c2a10-0001-4000-8000-000000000001");
    private static final UUID FLOOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111101");
    private static final UUID POI_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private BuildingRepository buildingRepository;
    @Mock
    private BuildingDirectoryRepository buildingDirectoryRepository;
    @Mock
    private FloorRepository floorRepository;
    @Mock
    private FloorplanRepository floorplanRepository;
    @Mock
    private PoiCategoryRepository poiCategoryRepository;
    @Mock
    private MapVersionRepository mapVersionRepository;
    @Mock
    private PoiRepository poiRepository;

    @InjectMocks
    private PlaceDetailService placeDetailService;

    @BeforeEach
    void setUp() {
        Building mockBuilding = building();
        MapVersion mockMapVersion = mapVersion();
        Floor mockFloor = floor(mockBuilding);
        Floorplan mockFloorplan = floorplan(mockFloor);
        Poi mockPoi = poi(mockFloor, mockMapVersion, "구찌", "22320326");
        BuildingDirectory mockDirectory = directory(mockMapVersion);

        lenient().when(buildingDirectoryRepository.findByIdAndIsPublicTrue(BUILDING_ID)).thenReturn(Optional.of(mockDirectory));
        lenient().when(floorRepository.findAllByBuilding_IdOrderByLevelDesc(BUILDING_ID)).thenReturn(List.of(mockFloor));
        lenient().when(floorplanRepository.findAllByFloorIdInAndIsCurrentTrue(anyList())).thenReturn(List.of(mockFloorplan));
        lenient().when(poiRepository.findAllByMapVersionIdWithFloor(MAP_VERSION_ID)).thenReturn(List.of(mockPoi));
        lenient().when(mapVersionRepository.findFirstByBuildingIdAndMapTypeAndStatusOrderByCreatedAtDesc(BUILDING_ID, com.insideout.backend.domain.map.enums.MapType.BUILDING, "published"))
                .thenReturn(Optional.of(mockMapVersion));
    }

    @Test
    void getDetail_byExternalApiId_returnsBuildingDetailWithFloorsAndPois() {
        Building mockBuilding = building();
        when(buildingRepository.findFirstByExternalApiIdOrderByCreatedAtDescIdDesc("18217490")).thenReturn(Optional.of(mockBuilding));

        Optional<PlaceDetailResponse> result = placeDetailService.getDetail(null, "18217490");

        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("신세계백화점 본점 디 에스테이트");
        assertThat(result.get().address()).isEqualTo("서울특별시 중구 퇴계로 77");
        assertThat(result.get().isRegistered()).isTrue();
        assertThat(result.get().hasIndoorMap()).isTrue();
        assertThat(result.get().floors()).hasSize(1);
        assertThat(result.get().floors().get(0).name()).isEqualTo("1F");
        assertThat(result.get().floors().get(0).pois()).extracting(PlaceDetailResponse.PoiResponse::name)
                .containsExactly("구찌");
    }

    @Test
    void getDetail_byPoiPlaceId_returnsSelectedPoiNameAndBuildingDetail() {
        Building mockBuilding = building();
        MapVersion mockMapVersion = mapVersion();
        Floor mockFloor = floor(mockBuilding);
        Poi mockPoi = poi(mockFloor, mockMapVersion, "구찌", "22320326");

        when(buildingRepository.findById(POI_ID)).thenReturn(Optional.empty());
        when(poiRepository.findByIdWithFloorAndMapVersion(POI_ID)).thenReturn(Optional.of(mockPoi));

        Optional<PlaceDetailResponse> result = placeDetailService.getDetail(POI_ID.toString(), null);

        assertThat(result).isPresent();
        assertThat(result.get().placeId()).isEqualTo(BUILDING_ID);
        assertThat(result.get().poiId()).isEqualTo(POI_ID);
        assertThat(result.get().externalApiId()).isEqualTo("18217490");
        assertThat(result.get().name()).isEqualTo("신세계백화점 본점 디 에스테이트");
        assertThat(result.get().isRegistered()).isTrue();
    }

    @Test
    void getDetail_withoutFloorplan_returnsFloorsAndPoisAsIndoorMapTrue() {
        Building mockBuilding = building();
        MapVersion mockMapVersion = mapVersion();
        Floor mockFloor = floor(mockBuilding);
        Poi mockPoi = poi(mockFloor, mockMapVersion, "구찌", "22320326");
        BuildingDirectory mockDirectory = directory(mockMapVersion);

        when(buildingRepository.findFirstByExternalApiIdOrderByCreatedAtDescIdDesc("18217490")).thenReturn(Optional.of(mockBuilding));
        when(buildingDirectoryRepository.findByIdAndIsPublicTrue(BUILDING_ID)).thenReturn(Optional.of(mockDirectory));
        when(floorRepository.findAllByBuilding_IdOrderByLevelDesc(BUILDING_ID)).thenReturn(List.of(mockFloor));
        when(floorplanRepository.findAllByFloorIdInAndIsCurrentTrue(anyList())).thenReturn(List.of());
        when(poiRepository.findAllByMapVersionIdWithFloor(MAP_VERSION_ID)).thenReturn(List.of(mockPoi));

        Optional<PlaceDetailResponse> result = placeDetailService.getDetail(null, "18217490");

        assertThat(result).isPresent();
        assertThat(result.get().hasIndoorMap()).isTrue();
        assertThat(result.get().floors()).hasSize(1);
        assertThat(result.get().floors().get(0).pois()).extracting(PlaceDetailResponse.PoiResponse::name)
                .containsExactly("구찌");
    }

    @Test
    void getDetail_filtersFacilityPoisFromResponse() {
        Building mockBuilding = building();
        MapVersion mockMapVersion = mapVersion();
        Floor mockFloor = floor(mockBuilding);
        Floorplan mockFloorplan = floorplan(mockFloor);
        Poi storePoi = poi(mockFloor, mockMapVersion, "구찌", "22320326", 100L);
        Poi elevatorPoi = poi(mockFloor, mockMapVersion, "elevator", null, 200L);
        Poi restroomPoi = poi(mockFloor, mockMapVersion, "여자 화장실", null, 300L);
        BuildingDirectory mockDirectory = directory(mockMapVersion);

        PoiCategory elevatorCategory = poiCategory(200L, "facility.elevator");
        PoiCategory restroomCategory = poiCategory(300L, "facility.restroom");
        PoiCategory storeCategory = poiCategory(100L, "store.retail");

        when(buildingRepository.findFirstByExternalApiIdOrderByCreatedAtDescIdDesc("18217490")).thenReturn(Optional.of(mockBuilding));
        when(buildingDirectoryRepository.findByIdAndIsPublicTrue(BUILDING_ID)).thenReturn(Optional.of(mockDirectory));
        when(floorRepository.findAllByBuilding_IdOrderByLevelDesc(BUILDING_ID)).thenReturn(List.of(mockFloor));
        when(floorplanRepository.findAllByFloorIdInAndIsCurrentTrue(anyList())).thenReturn(List.of(mockFloorplan));
        when(poiRepository.findAllByMapVersionIdWithFloor(MAP_VERSION_ID)).thenReturn(List.of(storePoi, elevatorPoi, restroomPoi));
        when(poiCategoryRepository.findAllById(any())).thenReturn(List.of(storeCategory, elevatorCategory, restroomCategory));

        Optional<PlaceDetailResponse> result = placeDetailService.getDetail(null, "18217490");

        assertThat(result).isPresent();
        assertThat(result.get().floors()).hasSize(1);
        assertThat(result.get().floors().get(0).pois()).extracting(PlaceDetailResponse.PoiResponse::name)
                .containsExactly("구찌");
    }

    private Building building() {
        Building building = mock(Building.class);
        lenient().when(building.getId()).thenReturn(BUILDING_ID);
        lenient().when(building.getName()).thenReturn("신세계백화점 본점 디 에스테이트");
        lenient().when(building.getAddress()).thenReturn("서울특별시 중구 퇴계로 77");
        lenient().when(building.getExternalApiId()).thenReturn("18217490");
        return building;
    }

    private BuildingDirectory directory(MapVersion publishedVersion) {
        BuildingDirectory directory = mock(BuildingDirectory.class);
        lenient().when(directory.getId()).thenReturn(BUILDING_ID);
        lenient().when(directory.getName()).thenReturn("신세계백화점 본점 디 에스테이트");
        lenient().when(directory.getAddress()).thenReturn("서울특별시 중구 퇴계로 77");
        lenient().when(directory.isPublic()).thenReturn(true);
        lenient().when(directory.getPublishedVersion()).thenReturn(publishedVersion);
        return directory;
    }

    private MapVersion mapVersion() {
        MapVersion mapVersion = mock(MapVersion.class);
        lenient().when(mapVersion.getId()).thenReturn(MAP_VERSION_ID);
        return mapVersion;
    }

    private Floor floor(Building building) {
        Floor floor = mock(Floor.class);
        lenient().when(floor.getId()).thenReturn(FLOOR_ID);
        lenient().when(floor.getLevel()).thenReturn(1);
        lenient().when(floor.getName()).thenReturn("1F");
        lenient().when(floor.getBuilding()).thenReturn(building);
        return floor;
    }

    private Floorplan floorplan(Floor floor) {
        Floorplan floorplan = mock(Floorplan.class);
        lenient().when(floorplan.getFloor()).thenReturn(floor);
        return floorplan;
    }

    private Poi poi(Floor floor, MapVersion mapVersion, String name, String externalApiId) {
        return poi(floor, mapVersion, name, externalApiId, null);
    }

    private Poi poi(Floor floor, MapVersion mapVersion, String name, String externalApiId, Long categoryId) {
        Poi poi = mock(Poi.class);
        lenient().when(poi.getId()).thenReturn(POI_ID);
        lenient().when(poi.getName()).thenReturn(name);
        lenient().when(poi.getExternalApiId()).thenReturn(externalApiId);
        lenient().when(poi.getCategoryId()).thenReturn(categoryId);
        lenient().when(poi.getFloor()).thenReturn(floor);
        lenient().when(poi.getMapVersion()).thenReturn(mapVersion);
        return poi;
    }

    private PoiCategory poiCategory(Long id, String code) {
        PoiCategory category = mock(PoiCategory.class);
        lenient().when(category.getId()).thenReturn(id);
        lenient().when(category.getCode()).thenReturn(code);
        return category;
    }
}
