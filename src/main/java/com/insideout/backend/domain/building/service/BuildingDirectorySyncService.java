package com.insideout.backend.domain.building.service;

import com.insideout.backend.domain.building.entity.Building;
import com.insideout.backend.domain.building.entity.BuildingDirectory;
import com.insideout.backend.domain.building.repository.BuildingDirectoryRepository;
import com.insideout.backend.domain.building.repository.BuildingRepository;
import com.insideout.backend.domain.map.entity.MapVersion;
import com.insideout.backend.domain.map.enums.MapType;
import com.insideout.backend.domain.map.repository.MapVersionRepository;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BuildingDirectorySyncService {

    private static final int DEFAULT_BATCH_SIZE = 500;
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private final BuildingRepository buildingRepository;
    private final BuildingDirectoryRepository buildingDirectoryRepository;
    private final MapVersionRepository mapVersionRepository;

    @Transactional
    public BuildingDirectory sync(Building building) {
        if (building == null || building.getId() == null || building.getTenant() == null) {
            return null;
        }

        MapVersion publishedVersion = mapVersionRepository
                .findFirstByBuildingIdAndMapTypeAndStatusOrderByCreatedAtDesc(
                        building.getId(),
                        MapType.BUILDING,
                        "published"
                )
                .orElse(null);

        Polygon footprint = building.getFootprint();
        BuildingDirectory existingDirectory = buildingDirectoryRepository
                .findByIdAndTenant_Id(building.getId(), building.getTenant().getId())
                .orElse(null);
        Point centroid = footprint != null
                ? footprint.getCentroid()
                : existingDirectory != null && existingDirectory.getCentroid() != null
                        ? existingDirectory.getCentroid()
                        : resolveMetaLocationPoint(building).orElse(null);
        Polygon bbox = footprint != null
                ? footprint
                : existingDirectory != null && existingDirectory.getBbox() != null
                        ? existingDirectory.getBbox()
                        : resolveMetaLocationBbox(building).orElse(null);

        BuildingDirectory directory = buildingDirectoryRepository.save(
                BuildingDirectory.builder()
                        .id(building.getId())
                        .tenant(building.getTenant())
                        .campus(building.getCampus())
                        .name(building.getName())
                        .address(building.getAddress())
                        .category(resolveCategory(building))
                        .centroid(centroid)
                        .bbox(bbox)
                        .isPublic(publishedVersion != null)
                        .publishedVersion(publishedVersion)
                        .build()
        );

        return directory;
    }

    @Transactional
    public List<BuildingDirectory> syncAll(List<Building> buildings) {
        if (buildings == null || buildings.isEmpty()) {
            return List.of();
        }

        return buildings.stream()
                .map(this::sync)
                .toList();
    }

    public int syncAllExisting(int batchSize) {
        int normalizedBatchSize = batchSize < 1 ? DEFAULT_BATCH_SIZE : batchSize;
        int pageNumber = 0;
        int syncedCount = 0;

        Page<Building> page;
        do {
            page = buildingRepository.findAll(PageRequest.of(
                    pageNumber,
                    normalizedBatchSize,
                    Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
            ));
            syncedCount += syncAll(page.getContent()).size();
            pageNumber++;
        } while (page.hasNext());

        return syncedCount;
    }

    private String resolveCategory(Building building) {
        if (building.getMeta() == null) {
            return null;
        }
        Object category = building.getMeta().get("category");
        return category == null ? null : String.valueOf(category);
    }

    @SuppressWarnings("unchecked")
    private Optional<Point> resolveMetaLocationPoint(Building building) {
        if (building == null || building.getMeta() == null) {
            return Optional.empty();
        }

        Object location = building.getMeta().get("location");
        if (!(location instanceof Map<?, ?> locationMap)) {
            return Optional.empty();
        }

        Double latitude = toDouble(locationMap.get("latitude"));
        Double longitude = toDouble(locationMap.get("longitude"));
        if (latitude == null || longitude == null) {
            return Optional.empty();
        }

        Coordinate coordinate = new Coordinate(longitude, latitude);
        Point point = GEOMETRY_FACTORY.createPoint(coordinate);
        point.setSRID(4326);
        return Optional.of(point);
    }

    private Optional<Polygon> resolveMetaLocationBbox(Building building) {
        return resolveMetaLocationPoint(building).map(point -> {
            double delta = 0.00012;
            Coordinate[] coordinates = new Coordinate[]{
                    new Coordinate(point.getX() - delta, point.getY() - delta),
                    new Coordinate(point.getX() + delta, point.getY() - delta),
                    new Coordinate(point.getX() + delta, point.getY() + delta),
                    new Coordinate(point.getX() - delta, point.getY() + delta),
                    new Coordinate(point.getX() - delta, point.getY() - delta)
            };
            Polygon polygon = GEOMETRY_FACTORY.createPolygon(coordinates);
            polygon.setSRID(4326);
            return polygon;
        });
    }

    private Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return Double.parseDouble(stringValue);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
