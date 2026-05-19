package com.insideout.backend.domain.building.entity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.insideout.backend.domain.building.exception.BuildingErrorCode;
import com.insideout.backend.domain.building.exception.BuildingException;
import com.insideout.backend.domain.tenant.entity.Tenant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class BuildingTest {

    @Test
    void constructorRejectsCampusFromDifferentTenant() {
        Tenant buildingTenant = tenantWithId(UUID.randomUUID());
        Tenant campusTenant = tenantWithId(UUID.randomUUID());
        Campus campus = Campus.builder()
                .tenant(campusTenant)
                .name("테스트 캠퍼스")
                .build();

        assertThatThrownBy(() -> Building.builder()
                .tenant(buildingTenant)
                .campus(campus)
                .name("테스트 건물")
                .entranceCount(0)
                .build())
                .isInstanceOf(BuildingException.class)
                .extracting("errorCode")
                .isEqualTo(BuildingErrorCode.BUILDING_CAMPUS_TENANT_MISMATCH);
    }

    @Test
    void buildingDirectoryConstructorRejectsCampusFromDifferentTenant() {
        Tenant directoryTenant = tenantWithId(UUID.randomUUID());
        Tenant campusTenant = tenantWithId(UUID.randomUUID());
        Campus campus = Campus.builder()
                .tenant(campusTenant)
                .name("테스트 캠퍼스")
                .build();

        assertThatThrownBy(() -> BuildingDirectory.builder()
                .id(UUID.randomUUID())
                .tenant(directoryTenant)
                .campus(campus)
                .name("테스트 건물")
                .build())
                .isInstanceOf(BuildingException.class)
                .extracting("errorCode")
                .isEqualTo(BuildingErrorCode.BUILDING_DIRECTORY_CAMPUS_TENANT_MISMATCH);
    }

    @Test
    void campusMapConstructorRejectsCampusFromDifferentTenantId() {
        UUID mapTenantId = UUID.randomUUID();
        Tenant campusTenant = tenantWithId(UUID.randomUUID());
        Campus campus = Campus.builder()
                .tenant(campusTenant)
                .name("테스트 캠퍼스")
                .build();

        assertThatThrownBy(() -> CampusMap.builder()
                .tenantId(mapTenantId)
                .campus(campus)
                .bucketName("test-bucket")
                .objectKey("campus-map.png")
                .widthPx(100)
                .heightPx(100)
                .build())
                .isInstanceOf(BuildingException.class)
                .extracting("errorCode")
                .isEqualTo(BuildingErrorCode.CAMPUS_MAP_TENANT_MISMATCH);
    }

    private Tenant tenantWithId(UUID id) {
        Tenant tenant = Tenant.builder()
                .slug(id.toString())
                .displayName("테스트 테넌트")
                .build();
        ReflectionTestUtils.setField(tenant, "id", id);
        return tenant;
    }
}
