package com.insideout.backend.domain.building.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.insideout.backend.domain.building.dto.CoordinateDTO;
import com.insideout.backend.domain.building.dto.request.CampusCreateRequestDTO;
import com.insideout.backend.domain.building.dto.response.CampusMapResponseDTO;
import com.insideout.backend.domain.building.dto.response.CampusResponseDTO;
import com.insideout.backend.domain.building.entity.Campus;
import com.insideout.backend.domain.building.entity.CampusMap;
import com.insideout.backend.domain.building.exception.BuildingErrorCode;
import com.insideout.backend.domain.building.exception.BuildingException;
import com.insideout.backend.domain.building.repository.CampusMapRepository;
import com.insideout.backend.domain.building.repository.CampusRepository;
import com.insideout.backend.domain.tenant.entity.Tenant;
import com.insideout.backend.domain.tenant.facade.TenantQueryFacade;
import com.insideout.backend.domain.tenant.repository.TenantRepository;
import com.insideout.backend.domain.user.entity.User;
import com.insideout.backend.domain.user.repository.UserRepository;
import com.insideout.backend.global.infra.storage.service.ImageStorageService;
import com.insideout.backend.global.infra.storage.service.S3StorageService;
import com.insideout.backend.domain.user.enums.GlobalRole;
import com.insideout.backend.global.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;

@ExtendWith(MockitoExtension.class)
class CampusServiceTest {

    @Mock
    private CampusRepository campusRepository;

    @Mock
    private CampusMapRepository campusMapRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ImageStorageService imageStorageService;

    @Mock
    private S3StorageService s3StorageService;

    @Mock
    private TenantQueryFacade tenantQueryFacade;

    @InjectMocks
    private CampusService campusService;

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private UUID tenantId;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        tenant = Tenant.builder()
                .displayName("Test Tenant")
                .slug("test-tenant")
                .build();
        // Reflection or setter to set ID since it is @GeneratedValue UUID
        try {
            java.lang.reflect.Field idField = Tenant.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(tenant, tenantId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void createCampus_succeeds() {
        CampusCreateRequestDTO request = new CampusCreateRequestDTO(
                "Test Campus",
                "Test Address",
                List.of(
                        new CoordinateDTO(127.0, 37.0),
                        new CoordinateDTO(127.1, 37.0),
                        new CoordinateDTO(127.1, 37.1),
                        new CoordinateDTO(127.0, 37.1)
                ),
                new CoordinateDTO(127.05, 37.05),
                new CoordinateDTO(127.0, 37.0),
                "Gate 1",
                Map.of("key", "value")
        );

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(campusRepository.save(any(Campus.class))).thenAnswer(invocation -> {
            Campus saved = invocation.getArgument(0);
            java.lang.reflect.Field idField = Campus.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(saved, UUID.randomUUID());
            java.lang.reflect.Field createdField = Campus.class.getDeclaredField("createdAt");
            createdField.setAccessible(true);
            createdField.set(saved, OffsetDateTime.now());
            return saved;
        });

        CampusResponseDTO response = campusService.createCampus(tenantId, request);

        assertThat(response.name()).isEqualTo("Test Campus");
        assertThat(response.address()).isEqualTo("Test Address");
        assertThat(response.primaryEntranceName()).isEqualTo("Gate 1");
        assertThat(response.meta()).containsEntry("key", "value");

        ArgumentCaptor<Campus> captor = ArgumentCaptor.forClass(Campus.class);
        verify(campusRepository).save(captor.capture());
        Campus captured = captor.getValue();
        assertThat(captured.getBoundary().getCoordinates()).hasSize(5); // closed ring
        assertThat(captured.getCentroid().getX()).isEqualTo(127.05);
        assertThat(captured.getPrimaryEntrance().getY()).isEqualTo(37.0);
    }

    @Test
    void createCampus_throwsTenantNotFound() {
        CampusCreateRequestDTO request = new CampusCreateRequestDTO(
                "Test Campus", "Address", List.of(), null, new CoordinateDTO(127.0, 37.0), "Gate", null
        );
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> campusService.createCampus(tenantId, request))
                .isInstanceOf(BuildingException.class)
                .extracting(ex -> ((BuildingException) ex).getErrorCode())
                .isEqualTo(BuildingErrorCode.TENANT_NOT_FOUND);
    }

    @Test
    void uploadCampusMap_succeeds() throws Exception {
        UUID campusId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CustomUserDetails userDetails = new CustomUserDetails(userId, "test@example.com", "passwordHash", GlobalRole.TENANT_USER);

        Campus campus = Campus.builder()
                .tenant(tenant)
                .name("Campus")
                .primaryEntrance(GEOMETRY_FACTORY.createPoint(new Coordinate(127.0, 37.0)))
                .build();
        java.lang.reflect.Field campusIdField = Campus.class.getDeclaredField("id");
        campusIdField.setAccessible(true);
        campusIdField.set(campus, campusId);

        User uploader = User.builder().email("test@example.com").build();
        byte[] pngBytes = createTinyPngBytes();
        MockMultipartFile file = new MockMultipartFile("file", "map.png", "image/png", pngBytes);

        when(tenantQueryFacade.isUserMemberOfTenant(userId, tenantId)).thenReturn(true);
        when(campusRepository.findByIdAndTenant_IdForUpdate(campusId, tenantId)).thenReturn(Optional.of(campus));
        when(userRepository.findById(userId)).thenReturn(Optional.of(uploader));
        when(imageStorageService.uploadCampusMapImage(tenantId, campusId, file))
                .thenReturn("s3://my-bucket/tenants/" + tenantId + "/campuses/" + campusId + "/maps/file.png");
        when(s3StorageService.defaultBucket()).thenReturn("my-bucket");

        when(campusMapRepository.save(any(CampusMap.class))).thenAnswer(invocation -> {
            CampusMap saved = invocation.getArgument(0);
            java.lang.reflect.Field mapIdField = CampusMap.class.getDeclaredField("id");
            mapIdField.setAccessible(true);
            mapIdField.set(saved, UUID.randomUUID());
            java.lang.reflect.Field uploadedAtField = CampusMap.class.getDeclaredField("uploadedAt");
            uploadedAtField.setAccessible(true);
            uploadedAtField.set(saved, OffsetDateTime.now());
            return saved;
        });

        CampusMapResponseDTO response = campusService.uploadCampusMap(tenantId, campusId, file, userDetails);

        assertThat(response.campusId()).isEqualTo(campusId);
        assertThat(response.imageUrl()).contains("s3://my-bucket/");
        assertThat(response.widthPx()).isEqualTo(10);
        assertThat(response.heightPx()).isEqualTo(10);
        assertThat(response.isCurrent()).isTrue();

        verify(campusMapRepository).deactivateCurrentMapsByCampusId(campusId);
    }

    private byte[] createTinyPngBytes() throws IOException {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }
}
