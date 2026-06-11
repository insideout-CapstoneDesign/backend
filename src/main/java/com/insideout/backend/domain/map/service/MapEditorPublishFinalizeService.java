package com.insideout.backend.domain.map.service;

import com.insideout.backend.domain.building.entity.Building;
import com.insideout.backend.domain.building.entity.BuildingEntranceMapping;
import com.insideout.backend.domain.building.entity.Campus;
import com.insideout.backend.domain.building.entity.Floor;
import com.insideout.backend.domain.building.entity.Floorplan;
import com.insideout.backend.domain.building.entity.FloorplanCalibration;
import com.insideout.backend.domain.building.repository.BuildingEntranceMappingRepository;
import com.insideout.backend.domain.building.repository.FloorplanCalibrationRepository;
import com.insideout.backend.domain.building.repository.FloorplanRepository;
import com.insideout.backend.domain.building.service.BuildingDirectorySyncService;
import com.insideout.backend.domain.map.entity.MapVersion;
import com.insideout.backend.domain.map.entity.Node;
import com.insideout.backend.domain.map.entity.Poi;
import com.insideout.backend.domain.map.enums.MapType;
import com.insideout.backend.domain.map.repository.MapVersionRepository;
import com.insideout.backend.domain.map.repository.NodeRepository;
import com.insideout.backend.domain.map.repository.PoiRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MapEditorPublishFinalizeService {

    private final MapVersionRepository mapVersionRepository;
    private final BuildingEntranceMappingRepository buildingEntranceMappingRepository;
    private final FloorplanRepository floorplanRepository;
    private final FloorplanCalibrationRepository floorplanCalibrationRepository;
    private final NodeRepository nodeRepository;
    private final PoiRepository poiRepository;
    private final BuildingDirectorySyncService buildingDirectorySyncService;
    @PersistenceContext
    private final EntityManager entityManager;

    public void finalizePublishedBuildingDraft(
            UUID tenantId,
            Building building,
            List<Floor> floors,
            MapVersion draftMapVersion
    ) {
        archiveExistingPublishedVersions(building.getId());

        draftMapVersion.publish();
        mapVersionRepository.save(draftMapVersion);

        updateBuildingPublishState(building);
        syncPublishedGeometry(tenantId, building, floors, draftMapVersion);
    }

    private void archiveExistingPublishedVersions(UUID buildingId) {
        List<MapVersion> existingPublished = mapVersionRepository
                .findAllByBuildingIdAndMapTypeAndStatus(buildingId, MapType.BUILDING, "published");
        for (MapVersion publishedVersion : existingPublished) {
            publishedVersion.archive();
        }
        // Flush archive updates first so the partial unique index on published versions
        // sees no active published row before we promote the draft version.
        mapVersionRepository.saveAllAndFlush(existingPublished);
    }

    private void updateBuildingPublishState(Building building) {
        building.updateActivationStatus("active");

        long entranceCount = nodeRepository.countByMapVersion_Building_IdAndKindCodeAndMapVersion_Status(
                building.getId(),
                "entrance",
                "published"
        );
        building.updateEntranceCount((int) entranceCount);

        if (building.getTenant() != null && !"approved".equals(building.getTenant().getStatus())) {
            building.getTenant().updateStatus("approved");
        }

        buildingDirectorySyncService.sync(building);
    }

    private void syncPublishedGeometry(
            UUID tenantId,
            Building building,
            List<Floor> floors,
            MapVersion draftMapVersion
    ) {
        Campus campus = building.getCampus();
        if (campus == null || campus.getMeta() == null) {
            return;
        }

        Object gatesValue = campus.getMeta().get("gates");
        if (!(gatesValue instanceof List<?> gates)) {
            return;
        }

        Map<String, Coordinate> gateCoordsById = new HashMap<>();
        for (Object item : gates) {
            if (!(item instanceof Map<?, ?> entry)) {
                continue;
            }

            Object idObj = entry.get("id");
            Object locObj = entry.get("location");
            if (idObj == null || !(locObj instanceof Map<?, ?> loc)) {
                continue;
            }

            Object lonObj = loc.get("longitude");
            Object latObj = loc.get("latitude");
            if (lonObj instanceof Number lonNum && latObj instanceof Number latNum) {
                gateCoordsById.put(
                        String.valueOf(idObj),
                        new Coordinate(lonNum.doubleValue(), latNum.doubleValue())
                );
            }
        }

        List<MapPointPair> pairs = collectAffinePointPairs(tenantId, building.getId(), draftMapVersion.getId(), gateCoordsById);
        List<Double> affine = calculateAffineTransform(pairs);
        if (affine == null) {
            return;
        }

        saveFloorplanCalibrations(tenantId, floors, affine);
        updatePublishedGeometry(draftMapVersion.getId(), affine);
    }

    private List<MapPointPair> collectAffinePointPairs(
            UUID tenantId,
            UUID buildingId,
            UUID mapVersionId,
            Map<String, Coordinate> gateCoordsById
    ) {
        List<MapPointPair> pairs = new ArrayList<>();

        List<BuildingEntranceMapping> mappings = buildingEntranceMappingRepository
                .findAllByTenantIdAndBuildingIdOrderByCreatedAtAsc(tenantId, buildingId);
        for (BuildingEntranceMapping mapping : mappings) {
            Node node = nodeRepository.findById(mapping.getEntranceNodeId()).orElse(null);
            Coordinate gateCoord = gateCoordsById.get(mapping.getCampusGateId());
            if (node != null && node.getGeomPx() != null && gateCoord != null) {
                pairs.add(new MapPointPair(
                        node.getGeomPx().getX(),
                        node.getGeomPx().getY(),
                        gateCoord.x,
                        gateCoord.y
                ));
            }
        }

        List<Poi> draftPois = poiRepository.findByMapVersionId(mapVersionId);
        for (Poi poi : draftPois) {
            if (poi.getGeomWgs84() != null && poi.getGeomPx() != null && poi.getExternalApiId() != null) {
                pairs.add(new MapPointPair(
                        poi.getGeomPx().getX(),
                        poi.getGeomPx().getY(),
                        poi.getGeomWgs84().getX(),
                        poi.getGeomWgs84().getY()
                ));
            }
        }

        return pairs;
    }

    private void saveFloorplanCalibrations(UUID tenantId, List<Floor> floors, List<Double> affine) {
        for (Floor floor : floors) {
            Floorplan floorplan = floorplanRepository.findByFloorIdAndIsCurrentTrue(floor.getId()).orElse(null);
            if (floorplan == null) {
                continue;
            }

            FloorplanCalibration calibration = floorplanCalibrationRepository
                    .findTopByFloorplanIdOrderByCreatedAtDesc(floorplan.getId())
                    .orElse(FloorplanCalibration.builder()
                            .tenantId(tenantId)
                            .floorplan(floorplan)
                            .gcp(Map.of())
                            .build());
            calibration.updateAffine(affine);
            floorplanCalibrationRepository.save(calibration);
        }
    }

    private void updatePublishedGeometry(UUID mapVersionId, List<Double> affine) {
        double a = affine.get(0);
        double b = affine.get(1);
        double d = affine.get(2);
        double e = affine.get(3);
        double xoff = affine.get(4);
        double yoff = affine.get(5);

        entityManager.createNativeQuery(
                        "UPDATE node n " +
                        "SET geom_wgs84 = CAST(ST_Force3D(ST_SetSRID(ST_Affine(n.geom_px, :a, :b, :d, :e, :xoff, :yoff), 4326)) AS geography) " +
                        "WHERE n.map_version_id = :mapVersionId"
                )
                .setParameter("a", a)
                .setParameter("b", b)
                .setParameter("d", d)
                .setParameter("e", e)
                .setParameter("xoff", xoff)
                .setParameter("yoff", yoff)
                .setParameter("mapVersionId", mapVersionId)
                .executeUpdate();

        entityManager.createNativeQuery(
                        "UPDATE poi p " +
                        "SET geom_wgs84 = CAST(ST_SetSRID(ST_Affine(p.geom_px, :a, :b, :d, :e, :xoff, :yoff), 4326) AS geography) " +
                        "WHERE p.map_version_id = :mapVersionId AND p.geom_wgs84 IS NULL"
                )
                .setParameter("a", a)
                .setParameter("b", b)
                .setParameter("d", d)
                .setParameter("e", e)
                .setParameter("xoff", xoff)
                .setParameter("yoff", yoff)
                .setParameter("mapVersionId", mapVersionId)
                .executeUpdate();

        entityManager.createNativeQuery(
                        "UPDATE edge e " +
                        "SET geom_wgs84 = CAST(ST_Force3D(ST_SetSRID(ST_Affine(e.geom_px, :a, :b, :d, :e, :xoff, :yoff), 4326)) AS geography) " +
                        "WHERE e.map_version_id = :mapVersionId"
                )
                .setParameter("a", a)
                .setParameter("b", b)
                .setParameter("d", d)
                .setParameter("e", e)
                .setParameter("xoff", xoff)
                .setParameter("yoff", yoff)
                .setParameter("mapVersionId", mapVersionId)
                .executeUpdate();

        entityManager.createNativeQuery(
                        "UPDATE edge e " +
                        "SET length_m = CAST(ST_Length(e.geom_wgs84) AS numeric(10,2)) " +
                        "WHERE e.map_version_id = :mapVersionId"
                )
                .setParameter("mapVersionId", mapVersionId)
                .executeUpdate();
    }

    private List<Double> calculateAffineTransform(List<MapPointPair> pairs) {
        int n = pairs.size();
        if (n < 3) {
            return null;
        }

        double sumX = 0;
        double sumY = 0;
        double sumXX = 0;
        double sumYY = 0;
        double sumXY = 0;
        double sumLon = 0;
        double sumLat = 0;
        double sumXLon = 0;
        double sumYLon = 0;
        double sumXLat = 0;
        double sumYLat = 0;

        for (MapPointPair pair : pairs) {
            double x = pair.pxX();
            double y = pair.pxY();
            double lon = pair.lon();
            double lat = pair.lat();

            sumX += x;
            sumY += y;
            sumXX += x * x;
            sumYY += y * y;
            sumXY += x * y;

            sumLon += lon;
            sumLat += lat;
            sumXLon += x * lon;
            sumYLon += y * lon;
            sumXLat += x * lat;
            sumYLat += y * lat;
        }

        double[][] matrix = {
                {sumXX, sumXY, sumX},
                {sumXY, sumYY, sumY},
                {sumX, sumY, (double) n}
        };

        double det = matrix[0][0] * (matrix[1][1] * matrix[2][2] - matrix[1][2] * matrix[2][1])
                - matrix[0][1] * (matrix[1][0] * matrix[2][2] - matrix[1][2] * matrix[2][0])
                + matrix[0][2] * (matrix[1][0] * matrix[2][1] - matrix[1][1] * matrix[2][0]);

        if (Math.abs(det) < 1e-12) {
            return null;
        }

        double[][] adj = {
                {
                        matrix[1][1] * matrix[2][2] - matrix[1][2] * matrix[2][1],
                        matrix[0][2] * matrix[2][1] - matrix[0][1] * matrix[2][2],
                        matrix[0][1] * matrix[1][2] - matrix[0][2] * matrix[1][1]
                },
                {
                        matrix[1][2] * matrix[2][0] - matrix[1][0] * matrix[2][2],
                        matrix[0][0] * matrix[2][2] - matrix[0][2] * matrix[2][0],
                        matrix[0][2] * matrix[1][0] - matrix[0][0] * matrix[1][2]
                },
                {
                        matrix[1][0] * matrix[2][1] - matrix[1][1] * matrix[2][0],
                        matrix[0][1] * matrix[2][0] - matrix[0][0] * matrix[2][1],
                        matrix[0][0] * matrix[1][1] - matrix[0][1] * matrix[1][0]
                }
        };

        double[][] inverse = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                inverse[i][j] = adj[i][j] / det;
            }
        }

        double[] lonVector = {sumXLon, sumYLon, sumLon};
        double a = inverse[0][0] * lonVector[0] + inverse[0][1] * lonVector[1] + inverse[0][2] * lonVector[2];
        double b = inverse[1][0] * lonVector[0] + inverse[1][1] * lonVector[1] + inverse[1][2] * lonVector[2];
        double xoff = inverse[2][0] * lonVector[0] + inverse[2][1] * lonVector[1] + inverse[2][2] * lonVector[2];

        double[] latVector = {sumXLat, sumYLat, sumLat};
        double d = inverse[0][0] * latVector[0] + inverse[0][1] * latVector[1] + inverse[0][2] * latVector[2];
        double e = inverse[1][0] * latVector[0] + inverse[1][1] * latVector[1] + inverse[1][2] * latVector[2];
        double yoff = inverse[2][0] * latVector[0] + inverse[2][1] * latVector[1] + inverse[2][2] * latVector[2];

        return List.of(a, b, d, e, xoff, yoff);
    }

    private record MapPointPair(double pxX, double pxY, double lon, double lat) {}
}
