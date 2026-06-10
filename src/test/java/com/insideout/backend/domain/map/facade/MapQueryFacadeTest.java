package com.insideout.backend.domain.map.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.insideout.backend.domain.building.repository.BuildingDirectoryRepository;
import com.insideout.backend.domain.building.repository.BuildingRepository;
import com.insideout.backend.domain.building.repository.FloorRepository;
import com.insideout.backend.domain.map.entity.Poi;
import com.insideout.backend.domain.map.repository.EdgeRepository;
import com.insideout.backend.domain.map.repository.MapVersionRepository;
import com.insideout.backend.domain.map.repository.NodeRepository;
import com.insideout.backend.domain.map.repository.ObstacleRepository;
import com.insideout.backend.domain.map.repository.PoiRepository;
import com.insideout.backend.domain.map.repository.VerticalConnectorNodeRepository;
import com.insideout.backend.domain.map.storage.MapAssetStorage;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MapQueryFacadeTest {

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
}
