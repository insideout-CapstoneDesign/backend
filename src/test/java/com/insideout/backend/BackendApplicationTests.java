package com.insideout.backend;

import com.insideout.backend.domain.ai.repository.AiDetectionRepository;
import com.insideout.backend.domain.ai.repository.AiJobRepository;
import com.insideout.backend.domain.ai.repository.CampusAiDetectionRepository;
import com.insideout.backend.domain.ai.repository.CampusAiJobRepository;
import com.insideout.backend.domain.building.repository.BuildingDirectoryRepository;
import com.insideout.backend.domain.building.repository.BuildingEntranceMappingRepository;
import com.insideout.backend.domain.building.repository.BuildingRepository;
import com.insideout.backend.domain.building.repository.CampusMapRepository;
import com.insideout.backend.domain.building.repository.CampusRepository;
import com.insideout.backend.domain.building.repository.FloorRepository;
import com.insideout.backend.domain.building.repository.FloorplanCalibrationRepository;
import com.insideout.backend.domain.building.repository.FloorplanRepository;
import com.insideout.backend.domain.map.repository.EdgeRepository;
import com.insideout.backend.domain.map.repository.FloorplanObjectRepository;
import com.insideout.backend.domain.map.repository.MapVersionRepository;
import com.insideout.backend.domain.map.repository.NodeRepository;
import com.insideout.backend.domain.map.repository.ObstacleRepository;
import com.insideout.backend.domain.map.repository.PoiCategoryRepository;
import com.insideout.backend.domain.map.repository.PoiRepository;
import com.insideout.backend.domain.map.repository.VerticalConnectorNodeRepository;
import com.insideout.backend.domain.map.repository.VerticalConnectorRepository;
import com.insideout.backend.domain.map.repository.ZoneRepository;
import com.insideout.backend.domain.map.storage.MapAssetStorage;
import com.insideout.backend.domain.tenant.repository.TenantMembershipRepository;
import com.insideout.backend.domain.tenant.repository.TenantRepository;
import com.insideout.backend.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration",
        "security.jwt.secret=test-secret-key-for-context-loads-123456",
        "security.jwt.access-token-expiration-ms=3600000",
        "security.jwt.refresh-token-expiration-ms=1209600000"
})
class BackendApplicationTests {

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private NodeRepository nodeRepository;

    @MockitoBean
    private BuildingDirectoryRepository buildingDirectoryRepository;

    @MockitoBean
    private BuildingRepository buildingRepository;

    @MockitoBean
    private BuildingEntranceMappingRepository buildingEntranceMappingRepository;

    @MockitoBean
    private PoiRepository poiRepository;

    @MockitoBean
    private PoiCategoryRepository poiCategoryRepository;

    @MockitoBean
    private EdgeRepository edgeRepository;

    @MockitoBean
    private FloorplanObjectRepository floorplanObjectRepository;

    @MockitoBean
    private MapVersionRepository mapVersionRepository;

    @MockitoBean
    private VerticalConnectorNodeRepository verticalConnectorNodeRepository;

    @MockitoBean
    private ObstacleRepository obstacleRepository;

    @MockitoBean
    private FloorplanRepository floorplanRepository;

    @MockitoBean
    private CampusMapRepository campusMapRepository;

    @MockitoBean
    private MapAssetStorage mapAssetStorage;

    @MockitoBean
    private TenantRepository tenantRepository;

    @MockitoBean
    private TenantMembershipRepository tenantMembershipRepository;

    @MockitoBean
    private AiJobRepository aiJobRepository;

    @MockitoBean
    private AiDetectionRepository aiDetectionRepository;

    @MockitoBean
    private CampusAiJobRepository campusAiJobRepository;

    @MockitoBean
    private CampusAiDetectionRepository campusAiDetectionRepository;

    @MockitoBean
    private FloorRepository floorRepository;

    @MockitoBean
    private CampusRepository campusRepository;

    @MockitoBean
    private FloorplanCalibrationRepository floorplanCalibrationRepository;

    @MockitoBean
    private VerticalConnectorRepository verticalConnectorRepository;

    @MockitoBean
    private ZoneRepository zoneRepository;

    @MockitoBean
    private FloorplanObjectRepository floorplanObjectRepository;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @MockitoBean
    private EntityManager entityManager;

    @MockitoBean
    private EntityManagerFactory entityManagerFactory;

    @MockitoBean
    private TransactionTemplate transactionTemplate;

    @Test
    void contextLoads() {
    }

}
