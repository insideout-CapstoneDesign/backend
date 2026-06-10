package com.insideout.backend.domain.map.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.insideout.backend.domain.building.entity.Building;
import com.insideout.backend.domain.building.entity.Floor;
import com.insideout.backend.domain.building.repository.BuildingDirectoryRepository;
import com.insideout.backend.domain.building.repository.BuildingRepository;
import com.insideout.backend.domain.building.repository.FloorRepository;
import com.insideout.backend.domain.map.entity.MapVersion;
import com.insideout.backend.domain.map.entity.Node;
import com.insideout.backend.domain.map.entity.Poi;
import com.insideout.backend.domain.map.enums.MapType;
import com.insideout.backend.domain.map.repository.EdgeRepository;
import com.insideout.backend.domain.map.repository.MapVersionRepository;
import com.insideout.backend.domain.map.repository.NodeRepository;
import com.insideout.backend.domain.map.repository.ObstacleRepository;
import com.insideout.backend.domain.map.repository.PoiRepository;
import com.insideout.backend.domain.map.repository.VerticalConnectorNodeRepository;
import com.insideout.backend.domain.map.storage.MapAssetDescriptor;
import com.insideout.backend.domain.map.storage.MapAssetStorage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MapQueryFacadeTest {

    private static GeometryFactory geometryFactory;

    @Mock
    private NodeRepository nodeRepository;

    @Mock
    private BuildingDirectoryRepository buildingDirectoryRepository;

    @Mock
    private BuildingRepository buildingRepository;

    @Mock
    private FloorRepository floorRepository;

    @Mock
    private PoiRepository poiRepository;

    @Mock
    private EdgeRepository edgeRepository;

    @Mock
    private MapVersionRepository mapVersionRepository;

    @Mock
    private VerticalConnectorNodeRepository verticalConnectorNodeRepository;

    @Mock
    private ObstacleRepository obstacleRepository;

    @Mock
    private MapAssetStorage mapAssetStorage;

    private MapQueryFacade mapQueryFacade;

    @BeforeAll
    static void setUpClass() {
        geometryFactory = new GeometryFactory();
    }

    @BeforeEach
    void setUp() {
        mapQueryFacade = new MapQueryFacade(
                nodeRepository,
                buildingDirectoryRepository,
                buildingRepository,
                floorRepository,
                poiRepository,
                edgeRepository,
                mapVersionRepository,
                verticalConnectorNodeRepository,
                obstacleRepository,
                mapAssetStorage
        );
    }

    @Test
    void findIndoorPoiDestinationReturnsEmptyWhenPoiHasAnchorButNoFloor() {
        Long publicId = 1001L;
        Poi poi = Poi.builder()
                .tenantId(UUID.randomUUID())
                .name("테스트 POI")
                .anchorNodeId(UUID.randomUUID())
                .build();
        ReflectionTestUtils.setField(poi, "publicId", publicId);

        when(poiRepository.findByPublicId(publicId)).thenReturn(Optional.of(poi));

        assertThat(mapQueryFacade.findIndoorPoiDestination(publicId)).isEmpty();
    }

    @Test
    void findCurrentBuildingFloorplansReturnsOnlyPublishedGraphFloors() {
        UUID buildingId = UUID.randomUUID();
        UUID publishedVersionId = UUID.randomUUID();
        UUID publishedFloorId = UUID.randomUUID();
        UUID draftOnlyFloorId = UUID.randomUUID();

        Building building = Building.builder()
                .name("테스트 건물")
                .build();
        ReflectionTestUtils.setField(building, "id", buildingId);

        Floor publishedFloor = Floor.builder()
                .building(building)
                .level(2)
                .name("2F")
                .build();
        ReflectionTestUtils.setField(publishedFloor, "id", publishedFloorId);

        Floor draftOnlyFloor = Floor.builder()
                .building(building)
                .level(1)
                .name("1F")
                .build();
        ReflectionTestUtils.setField(draftOnlyFloor, "id", draftOnlyFloorId);

        MapVersion publishedVersion = MapVersion.builder()
                .mapType(MapType.BUILDING)
                .building(building)
                .status("published")
                .label("published")
                .build();
        ReflectionTestUtils.setField(publishedVersion, "id", publishedVersionId);

        Node publishedNode = Node.builder()
                .mapVersion(publishedVersion)
                .floor(publishedFloor)
                .kindCode("corridor")
                .geomPx(geometryFactory.createPoint(new Coordinate(10, 10)))
                .build();

        when(mapVersionRepository.findFirstByBuildingIdAndMapTypeAndStatusOrderByCreatedAtDesc(
                buildingId,
                MapType.BUILDING,
                "published"
        )).thenReturn(Optional.of(publishedVersion));
        when(nodeRepository.findByMapVersionId(publishedVersionId)).thenReturn(List.of(publishedNode));
        when(floorRepository.findAllByBuilding_IdOrderByLevelDesc(buildingId))
                .thenReturn(List.of(publishedFloor, draftOnlyFloor));
        when(mapAssetStorage.findCurrentMapAsset(MapType.BUILDING, publishedFloorId))
                .thenReturn(Optional.of(new MapAssetDescriptor(
                        MapType.BUILDING,
                        publishedFloorId,
                        "bucket",
                        "2f.png",
                        "https://example.com/2f.png",
                        1000,
                        800
                )));

        List<MapQueryFacade.PublishedFloorplan> floorplans = mapQueryFacade.findCurrentBuildingFloorplans(buildingId);

        assertThat(floorplans)
                .singleElement()
                .satisfies(floorplan -> {
                    assertThat(floorplan.floorId()).isEqualTo(publishedFloorId);
                    assertThat(floorplan.floorName()).isEqualTo("2F");
                    assertThat(floorplan.mapImageUrl()).isEqualTo("https://example.com/2f.png");
                });
    }

    @Test
    void findIndoorPoiDestinationReturnsEmptyWhenNearestRoutableNodeIsTooFar() {
        Long publicId = 1002L;
        UUID mapVersionId = UUID.randomUUID();
        UUID floorId = UUID.randomUUID();
        UUID buildingId = UUID.randomUUID();

        Building building = Building.builder()
                .name("테스트 건물")
                .build();
        ReflectionTestUtils.setField(building, "id", buildingId);

        Floor floor = Floor.builder()
                .building(building)
                .level(1)
                .name("1F")
                .build();
        ReflectionTestUtils.setField(floor, "id", floorId);

        MapVersion mapVersion = MapVersion.builder()
                .mapType(MapType.BUILDING)
                .building(building)
                .status("published")
                .label("published")
                .build();
        ReflectionTestUtils.setField(mapVersion, "id", mapVersionId);

        Poi poi = Poi.builder()
                .tenantId(UUID.randomUUID())
                .mapVersion(mapVersion)
                .floor(floor)
                .name("멀리 있는 POI")
                .geomPx(geometryFactory.createPoint(new Coordinate(0, 0)))
                .build();
        ReflectionTestUtils.setField(poi, "publicId", publicId);

        Node farNode = Node.builder()
                .mapVersion(mapVersion)
                .floor(floor)
                .kindCode("corridor")
                .geomPx(geometryFactory.createPoint(new Coordinate(121, 0)))
                .build();
        ReflectionTestUtils.setField(farNode, "id", UUID.randomUUID());

        when(poiRepository.findByPublicId(publicId)).thenReturn(Optional.of(poi));
        when(nodeRepository.findNearestRoutableNodeByPixel(mapVersionId, floorId, 0.0, 0.0))
                .thenReturn(Optional.of(farNode));

        assertThat(mapQueryFacade.findIndoorPoiDestination(publicId)).isEmpty();
    }
}
