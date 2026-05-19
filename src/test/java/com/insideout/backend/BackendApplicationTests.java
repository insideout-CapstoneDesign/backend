package com.insideout.backend;

import com.insideout.backend.domain.building.repository.BuildingDirectoryRepository;
import com.insideout.backend.domain.building.repository.BuildingRepository;
import com.insideout.backend.domain.map.repository.NodeRepository;
import com.insideout.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void contextLoads() {
    }

}
