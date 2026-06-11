package com.insideout.backend.domain.map.service;

import com.insideout.backend.domain.ai.entity.AiDetection;
import com.insideout.backend.domain.map.entity.Edge;
import com.insideout.backend.domain.map.entity.FloorplanObject;
import com.insideout.backend.domain.map.entity.MapVersion;
import com.insideout.backend.domain.map.entity.Node;
import com.insideout.backend.domain.map.entity.Poi;
import com.insideout.backend.domain.map.entity.PoiCategory;
import com.insideout.backend.domain.map.entity.Zone;
import com.insideout.backend.domain.map.entity.ZoneKind;
import com.insideout.backend.domain.map.repository.EdgeRepository;
import com.insideout.backend.domain.map.repository.FloorplanObjectRepository;
import com.insideout.backend.domain.map.repository.NodeRepository;
import com.insideout.backend.domain.map.repository.PoiCategoryRepository;
import com.insideout.backend.domain.map.repository.PoiRepository;
import com.insideout.backend.domain.map.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MapEditorAiDraftInitializationService {

    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new org.locationtech.jts.geom.PrecisionModel(), 0);

    private static final double POI_TEXT_BUFFER_PX = 4.0;
    private static final double POI_TEXT_DISTANCE_PENALTY = 180.0;
    private static final double POI_TEXT_MAX_DISTANCE_PX = 140.0;

    private final NodeRepository nodeRepository;
    private final ZoneRepository zoneRepository;
    private final FloorplanObjectRepository floorplanObjectRepository;
    private final PoiRepository poiRepository;
    private final EdgeRepository edgeRepository;
    private final PoiCategoryRepository poiCategoryRepository;

    public void initializeFloorDraftFromAi(
            MapVersion draftMapVersion,
            com.insideout.backend.domain.building.entity.Floor floor,
            List<AiDetection> detections,
            boolean hasZones,
            boolean hasFloorplanObjects,
            boolean hasEdges,
            boolean hasNodes,
            boolean hasPois
    ) {
        Map<String, Long> poiCategoryIdsByCode = loadPoiCategoryIds(detections);

        List<Zone> zonesToSave = new ArrayList<>();
        List<FloorplanObject> floorplanObjectsToSave = new ArrayList<>();
        List<Node> explicitNodesToSave = new ArrayList<>();
        List<Poi> poisToSave = new ArrayList<>();
        List<AiDetection> edgeDetections = new ArrayList<>();
        List<AiDetection> textDetections = detections.stream()
                .filter(detection -> "text".equals(normalizeDetectType(detection.getDetectType())))
                .filter(detection -> extractReadableText(detection) != null)
                .toList();

        Map<String, Node> nodeCache = new LinkedHashMap<>();
        if (hasNodes || hasEdges) {
            nodeRepository.findByMapVersionIdAndFloorId(draftMapVersion.getId(), floor.getId()).forEach(
                    node -> nodeCache.put(toNodeCacheKey(node.getGeomPx()), node)
            );
        }

        for (AiDetection detection : detections) {
            String detectType = normalizeDetectType(detection.getDetectType());
            switch (detectType) {
                case "room" -> {
                    if (!hasZones) {
                        buildZone(draftMapVersion, floor, detection, ZoneKind.room)
                                .map(zone -> enrichZoneNameFromText(zone, textDetections))
                                .ifPresent(zonesToSave::add);
                    }
                }
                case "corridor", "walkable_area" -> {
                    if (!hasZones) {
                        buildZone(draftMapVersion, floor, detection, ZoneKind.corridor)
                                .map(zone -> enrichZoneNameFromText(zone, textDetections))
                                .ifPresent(zonesToSave::add);
                    }
                }
                case "blocked_area" -> {
                    if (!hasZones) {
                        buildZone(draftMapVersion, floor, detection, ZoneKind.restricted)
                                .map(zone -> enrichZoneNameFromText(zone, textDetections))
                                .ifPresent(zonesToSave::add);
                    }
                }
                case "wall" -> {
                    if (!hasFloorplanObjects) {
                        buildFloorplanObject(draftMapVersion, floor, detection, "wall")
                                .ifPresent(floorplanObjectsToSave::add);
                    }
                }
                case "door" -> {
                    if (!hasFloorplanObjects) {
                        buildFloorplanObject(draftMapVersion, floor, detection, "door")
                                .ifPresent(floorplanObjectsToSave::add);
                    }
                }
                case "node", "node_candidate", "entrance", "stair", "elevator", "escalator",
                        "center_junction", "center_endpoint" -> {
                    if (hasNodes) {
                        if (isVerticalFacilityDetectType(detectType) && !hasPois) {
                            buildPoi(draftMapVersion, floor, detection, poiCategoryIdsByCode)
                                    .map(poi -> enrichPoiNameFromText(poi, detection, textDetections))
                                    .ifPresent(poisToSave::add);
                        }
                        continue;
                    }

                    Point point = toPoint(detection.getGeomPx());
                    if (point == null) {
                        continue;
                    }

                    String key = toNodeCacheKey(point);
                    if (nodeCache.containsKey(key)) {
                        continue;
                    }

                    Node node = Node.builder()
                            .tenantId(draftMapVersion.getTenantId())
                            .mapVersion(draftMapVersion)
                            .floor(floor)
                            .kindCode(resolveNodeKindCode(detectType))
                            .geomPx(point)
                            .nameKo(resolveDetectionName(detection))
                            .properties(buildDetectionProperties(detection))
                            .source("ai")
                            .aiDetectionId(detection.getId())
                            .build();
                    explicitNodesToSave.add(node);
                    nodeCache.put(key, node);

                    if (isVerticalFacilityDetectType(detectType) && !hasPois) {
                        buildPoi(draftMapVersion, floor, detection, poiCategoryIdsByCode)
                                .map(poi -> enrichPoiNameFromText(poi, detection, textDetections))
                                .ifPresent(poisToSave::add);
                    }
                }
                case "edge", "edge_candidate" -> {
                    if (!hasEdges) {
                        edgeDetections.add(detection);
                    }
                }
                default -> buildPoi(draftMapVersion, floor, detection, poiCategoryIdsByCode)
                        .map(poi -> enrichPoiNameFromText(poi, detection, textDetections))
                        .ifPresent(poi -> {
                            if (!hasPois) {
                                poisToSave.add(poi);
                            }
                        });
            }
        }

        if (!zonesToSave.isEmpty()) {
            List<Zone> savedZones = zoneRepository.saveAll(ZoneDedupUtil.suppressDuplicateRoomZones(zonesToSave));
            savedZones.forEach(zone -> detections.stream()
                    .filter(detection -> Objects.equals(detection.getId(), extractAiDetectionId(zone)))
                    .findFirst()
                    .ifPresent(detection -> detection.markCommitted("zone", zone.getId())));
        }

        if (!floorplanObjectsToSave.isEmpty()) {
            List<FloorplanObject> savedObjects = floorplanObjectRepository.saveAll(floorplanObjectsToSave);
            savedObjects.forEach(object -> detections.stream()
                    .filter(detection -> Objects.equals(detection.getId(), object.getAiDetectionId()))
                    .findFirst()
                    .ifPresent(detection -> detection.markCommitted("floorplan_object", object.getId())));
        }

        if (!explicitNodesToSave.isEmpty()) {
            List<Node> savedNodes = nodeRepository.saveAll(explicitNodesToSave);
            nodeCache.clear();
            for (Node savedNode : savedNodes) {
                nodeCache.put(toNodeCacheKey(savedNode.getGeomPx()), savedNode);
                detections.stream()
                        .filter(detection -> Objects.equals(detection.getId(), savedNode.getAiDetectionId()))
                        .findFirst()
                        .ifPresent(detection -> detection.markCommitted("node", savedNode.getId()));
            }

            for (Poi poi : poisToSave) {
                if (poi.getAiDetectionId() != null) {
                    savedNodes.stream()
                            .filter(node -> Objects.equals(node.getAiDetectionId(), poi.getAiDetectionId()))
                            .findFirst()
                            .ifPresent(node -> poi.updateAnchorNodeId(node.getId()));
                }
            }
        }

        if (!poisToSave.isEmpty()) {
            List<Poi> savedPois = poiRepository.saveAll(suppressGenericPoiCandidatesNearFacilities(poisToSave));
            savedPois.forEach(poi -> detections.stream()
                    .filter(detection -> Objects.equals(detection.getId(), poi.getAiDetectionId()))
                    .findFirst()
                    .ifPresent(detection -> detection.markCommitted("poi", poi.getId())));
        }

        saveEdges(draftMapVersion, floor, edgeDetections, nodeCache);
    }

    private void saveEdges(
            MapVersion draftMapVersion,
            com.insideout.backend.domain.building.entity.Floor floor,
            List<AiDetection> edgeDetections,
            Map<String, Node> nodeCache
    ) {
        if (edgeDetections.isEmpty()) {
            return;
        }

        List<Node> implicitNodesToSave = new ArrayList<>();
        for (AiDetection detection : edgeDetections) {
            LineString lineString = toLineString(detection.getGeomPx());
            if (lineString == null || lineString.getNumPoints() < 2) {
                continue;
            }

            ensureNodeForCoordinate(draftMapVersion, floor, lineString.getCoordinateN(0), nodeCache, implicitNodesToSave);
            ensureNodeForCoordinate(draftMapVersion, floor, lineString.getCoordinateN(lineString.getNumPoints() - 1), nodeCache, implicitNodesToSave);
        }

        if (!implicitNodesToSave.isEmpty()) {
            List<Node> savedNodes = nodeRepository.saveAll(implicitNodesToSave);
            for (Node savedNode : savedNodes) {
                nodeCache.put(toNodeCacheKey(savedNode.getGeomPx()), savedNode);
            }
        }

        List<Edge> edgesToSave = new ArrayList<>();
        for (AiDetection detection : edgeDetections) {
            LineString lineString = toLineString(detection.getGeomPx());
            if (lineString == null || lineString.getNumPoints() < 2) {
                continue;
            }

            Node fromNode = nodeCache.get(toNodeCacheKey(lineString.getCoordinateN(0)));
            Node toNode = nodeCache.get(toNodeCacheKey(lineString.getCoordinateN(lineString.getNumPoints() - 1)));
            if (fromNode == null || toNode == null) {
                continue;
            }

            edgesToSave.add(
                    Edge.builder()
                            .tenantId(draftMapVersion.getTenantId())
                            .mapVersion(draftMapVersion)
                            .fromNode(fromNode)
                            .toNode(toNode)
                            .kindCode("walkway")
                            .geomPx(lineString)
                            .properties(buildDetectionProperties(detection))
                            .source("ai")
                            .aiDetectionId(detection.getId())
                            .build()
            );
        }

        if (!edgesToSave.isEmpty()) {
            List<Edge> savedEdges = edgeRepository.saveAll(edgesToSave);
            savedEdges.forEach(edge -> edgeDetections.stream()
                    .filter(detection -> Objects.equals(detection.getId(), edge.getAiDetectionId()))
                    .findFirst()
                    .ifPresent(detection -> detection.markCommitted("edge", edge.getId())));
        }
    }

    private void ensureNodeForCoordinate(
            MapVersion draftMapVersion,
            com.insideout.backend.domain.building.entity.Floor floor,
            Coordinate coordinate,
            Map<String, Node> nodeCache,
            List<Node> implicitNodesToSave
    ) {
        Point point = GEOMETRY_FACTORY.createPoint(coordinate);
        String key = toNodeCacheKey(point);
        if (nodeCache.containsKey(key)) {
            return;
        }

        Node node = Node.builder()
                .tenantId(draftMapVersion.getTenantId())
                .mapVersion(draftMapVersion)
                .floor(floor)
                .kindCode("corridor")
                .geomPx(point)
                .properties(Map.of("generatedFrom", "edge_candidate"))
                .source("ai")
                .build();
        nodeCache.put(key, node);
        implicitNodesToSave.add(node);
    }

    private Optional<Zone> buildZone(
            MapVersion mapVersion,
            com.insideout.backend.domain.building.entity.Floor floor,
            AiDetection detection,
            ZoneKind zoneKind
    ) {
        if (!(detection.getGeomPx() instanceof Polygon polygon)) {
            return Optional.empty();
        }
        return Optional.of(
                Zone.builder()
                        .tenantId(mapVersion.getTenantId())
                        .mapVersion(mapVersion)
                        .floor(floor)
                        .kind(zoneKind)
                        .name(null)
                        .geomPx(polygon)
                        .properties(buildDetectionProperties(detection))
                        .build()
        );
    }

    private Zone enrichZoneNameFromText(Zone zone, List<AiDetection> textDetections) {
        Polygon polygon = zone.getGeomPx();
        String bestText = findBestMatchingTextForZone(polygon, textDetections);
        if (bestText == null) {
            return zone;
        }

        return Zone.builder()
                .tenantId(zone.getTenantId())
                .mapVersion(zone.getMapVersion())
                .floor(zone.getFloor())
                .kind(zone.getKind())
                .name(bestText)
                .geomPx(zone.getGeomPx())
                .properties(zone.getProperties())
                .build();
    }

    private Optional<FloorplanObject> buildFloorplanObject(
            MapVersion mapVersion,
            com.insideout.backend.domain.building.entity.Floor floor,
            AiDetection detection,
            String kind
    ) {
        Geometry geometry = detection.getGeomPx();
        if (geometry == null) {
            return Optional.empty();
        }
        return Optional.of(
                FloorplanObject.builder()
                        .tenantId(mapVersion.getTenantId())
                        .mapVersion(mapVersion)
                        .floor(floor)
                        .kind(kind)
                        .geomPx(geometry)
                        .properties(buildDetectionProperties(detection))
                        .source("ai")
                        .aiDetectionId(detection.getId())
                        .build()
        );
    }

    private Optional<Poi> buildPoi(
            MapVersion mapVersion,
            com.insideout.backend.domain.building.entity.Floor floor,
            AiDetection detection,
            Map<String, Long> poiCategoryIdsByCode
    ) {
        if (!isPoiDetection(detection.getDetectType())) {
            return Optional.empty();
        }

        Point point = toPoint(detection.getGeomPx());
        if (point == null) {
            return Optional.empty();
        }

        Geometry geometry = detection.getGeomPx();
        Polygon footprint = geometry instanceof Polygon polygon ? polygon : null;
        String categoryCode = resolvePoiCategoryCode(detection.getDetectType());
        Long categoryId = categoryCode != null ? poiCategoryIdsByCode.get(categoryCode) : null;
        Map<String, Object> properties = new LinkedHashMap<>(buildDetectionProperties(detection));
        if (categoryCode != null) {
            properties.put("label", categoryCode);
        }

        return Optional.of(
                Poi.builder()
                        .tenantId(mapVersion.getTenantId())
                        .mapVersion(mapVersion)
                        .floor(floor)
                        .categoryId(categoryId)
                        .name(resolvePoiName(detection))
                        .code(categoryCode)
                        .geomPx(point)
                        .footprintPx(footprint)
                        .attrs(properties)
                        .source("ai")
                        .aiDetectionId(detection.getId())
                        .build()
        );
    }

    private Map<String, Long> loadPoiCategoryIds(List<AiDetection> detections) {
        Set<String> categoryCodes = detections.stream()
                .map(AiDetection::getDetectType)
                .map(this::resolvePoiCategoryCode)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        if (categoryCodes.isEmpty()) {
            return Map.of();
        }

        return poiCategoryRepository.findByCodeIn(categoryCodes).stream()
                .collect(java.util.stream.Collectors.toMap(
                        PoiCategory::getCode,
                        PoiCategory::getId,
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ));
    }

    private Poi enrichPoiNameFromText(Poi poi, AiDetection poiDetection, List<AiDetection> textDetections) {
        if (isFacilityPoiDetection(poiDetection)) {
            return poi;
        }

        String ownReadableText = extractReadableText(poiDetection);
        if (ownReadableText != null) {
            return poi;
        }

        String matchedText = findBestMatchingTextForPoi(poiDetection, textDetections);
        if (matchedText == null) {
            return poi;
        }

        return Poi.builder()
                .tenantId(poi.getTenantId())
                .mapVersion(poi.getMapVersion())
                .floor(poi.getFloor())
                .categoryId(poi.getCategoryId())
                .name(matchedText)
                .code(poi.getCode())
                .geomPx(poi.getGeomPx())
                .footprintPx(poi.getFootprintPx())
                .geomWgs84(poi.getGeomWgs84())
                .anchorNodeId(poi.getAnchorNodeId())
                .tags(poi.getTags())
                .attrs(poi.getAttrs())
                .externalApiId(poi.getExternalApiId())
                .source(poi.getSource())
                .aiDetectionId(poi.getAiDetectionId())
                .build();
    }

    private List<Poi> suppressGenericPoiCandidatesNearFacilities(List<Poi> pois) {
        List<Poi> facilityPois = pois.stream()
                .filter(this::isFacilityPoi)
                .toList();

        if (facilityPois.isEmpty()) {
            return pois;
        }

        List<Poi> filtered = new ArrayList<>();
        for (Poi poi : pois) {
            if (!isGenericPoiCandidate(poi)) {
                filtered.add(poi);
                continue;
            }

            boolean overlapsFacility = facilityPois.stream()
                    .anyMatch(facilityPoi -> isSameOrNearbyPoi(facilityPoi, poi));

            if (!overlapsFacility) {
                filtered.add(poi);
            }
        }
        return filtered;
    }

    private Map<String, Object> buildDetectionProperties(AiDetection detection) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("detectType", detection.getDetectType());
        if (detection.getConfidence() != null) {
            properties.put("confidence", detection.getConfidence().setScale(4, RoundingMode.HALF_UP));
        }
        if (detection.getOcrText() != null && !detection.getOcrText().isBlank()) {
            properties.put("ocrText", detection.getOcrText());
        }
        if (detection.getLabel() != null && !detection.getLabel().isBlank()) {
            properties.put("label", detection.getLabel());
        }
        properties.put("aiDetectionId", detection.getId());
        if (detection.getAttrs() != null && !detection.getAttrs().isEmpty()) {
            properties.putAll(detection.getAttrs());
        }
        return properties;
    }

    private String normalizeDetectType(String detectType) {
        return detectType == null ? "" : detectType.trim().toLowerCase();
    }

    private boolean isPoiDetection(String detectType) {
        return resolvePoiCategoryCode(detectType) != null || "poi_candidate".equals(normalizeDetectType(detectType));
    }

    private boolean isFacilityPoiDetection(AiDetection detection) {
        String categoryCode = resolvePoiCategoryCode(detection.getDetectType());
        return categoryCode != null && categoryCode.startsWith("facility.");
    }

    private String resolvePoiCategoryCode(String detectType) {
        return switch (normalizeDetectType(detectType)) {
            case "accessible_restroom" -> "facility.accessible_restroom";
            case "aed" -> "facility.aed";
            case "atm" -> "facility.atm";
            case "cafe" -> "store.cafe";
            case "clothing_alteration" -> "store.clothing_alteration";
            case "elevator" -> "facility.elevator";
            case "escalator" -> "facility.escalator";
            case "facility.elevator" -> "facility.elevator";
            case "facility.escalator" -> "facility.escalator";
            case "facility.infodesk" -> "facility.infodesk";
            case "facility.stair" -> "facility.stair";
            case "family_restroom" -> "facility.family_restroom";
            case "infodesk" -> "facility.infodesk";
            case "phone_charging" -> "facility.phone_charging";
            case "restroom_female" -> "facility.restroom_female";
            case "restroom_male" -> "facility.restroom_male";
            case "restroom_sign" -> "facility.restroom";
            case "shoe_repair" -> "store.shoe_repair";
            case "stair" -> "facility.stair";
            case "storage_locker" -> "facility.storage_locker";
            case "subway_station" -> "facility.subway_station";
            case "water_fountain" -> "facility.water_fountain";
            default -> null;
        };
    }

    private String resolvePoiName(AiDetection detection) {
        String preferred = extractReadableText(detection);
        if (preferred != null) {
            return preferred;
        }

        return switch (normalizeDetectType(detection.getDetectType())) {
            case "accessible_restroom" -> "장애인 화장실";
            case "aed" -> "AED";
            case "atm" -> "ATM";
            case "cafe" -> "카페";
            case "clothing_alteration" -> "수선실";
            case "elevator" -> "엘리베이터";
            case "escalator" -> "에스컬레이터";
            case "facility.elevator" -> "엘리베이터";
            case "facility.escalator" -> "에스컬레이터";
            case "facility.infodesk" -> "안내데스크";
            case "facility.stair" -> "계단";
            case "family_restroom" -> "가족 화장실";
            case "infodesk" -> "안내데스크";
            case "phone_charging" -> "휴대폰 충전";
            case "restroom_female" -> "여자 화장실";
            case "restroom_male" -> "남자 화장실";
            case "restroom_sign" -> "화장실";
            case "shoe_repair" -> "구두수선";
            case "stair" -> "계단";
            case "storage_locker" -> "물품보관함";
            case "subway_station" -> "지하철역";
            case "water_fountain" -> "정수기";
            case "poi_candidate" -> "POI 후보";
            default -> "POI";
        };
    }

    private boolean isVerticalFacilityDetectType(String detectType) {
        return switch (normalizeDetectType(detectType)) {
            case "stair", "elevator", "escalator" -> true;
            default -> false;
        };
    }

    private String findBestMatchingTextForPoi(AiDetection poiDetection, List<AiDetection> textDetections) {
        if (textDetections.isEmpty()) {
            return null;
        }

        Geometry poiGeometry = poiDetection.getGeomPx();
        Point poiPoint = toPoint(poiGeometry);
        if (poiGeometry == null || poiPoint == null) {
            return null;
        }

        String bestText = null;
        double bestScore = Double.MAX_VALUE;

        for (AiDetection textDetection : textDetections) {
            String candidateText = extractReadableText(textDetection);
            if (candidateText == null) {
                continue;
            }

            Point textPoint = toPoint(textDetection.getGeomPx());
            if (textPoint == null) {
                continue;
            }

            double distance = poiGeometry.distance(textPoint);
            boolean inside = poiGeometry instanceof Polygon polygon && polygon.buffer(POI_TEXT_BUFFER_PX).contains(textPoint);
            double score = inside ? distance : distance + POI_TEXT_DISTANCE_PENALTY;

            if (!inside && distance > POI_TEXT_MAX_DISTANCE_PX) {
                continue;
            }

            if (score < bestScore) {
                bestScore = score;
                bestText = candidateText;
            }
        }

        return bestText;
    }

    private String findBestMatchingTextForZone(Polygon zonePolygon, List<AiDetection> textDetections) {
        if (textDetections.isEmpty()) {
            return null;
        }

        Point centroid = zonePolygon.getCentroid();
        String bestText = null;
        double bestScore = Double.MAX_VALUE;

        for (AiDetection textDetection : textDetections) {
            String candidateText = extractReadableText(textDetection);
            if (candidateText == null) {
                continue;
            }

            Point textPoint = toPoint(textDetection.getGeomPx());
            if (textPoint == null) {
                continue;
            }

            boolean inside = zonePolygon.buffer(4).contains(textPoint);
            if (!inside) {
                continue;
            }

            double distanceScore = centroid != null ? centroid.distance(textPoint) : 0.0;
            double score = distanceScore;

            if (score < bestScore) {
                bestScore = score;
                bestText = candidateText;
            }
        }

        return bestText;
    }

    private String extractReadableText(AiDetection detection) {
        String ocrText = sanitizeNameCandidate(detection.getOcrText());
        if (ocrText != null) {
            return ocrText;
        }

        return sanitizeNameCandidate(detection.getLabel());
    }

    private String sanitizeNameCandidate(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            return null;
        }

        String normalized = trimmed.toLowerCase();
        if (normalized.startsWith("facility.")
                || normalized.startsWith("store.")
                || normalized.contains("_")
                || normalized.equals("poi_candidate")
                || normalized.equals("poi")) {
            return null;
        }

        return trimmed;
    }



    private String resolveDetectionName(AiDetection detection) {
        return extractReadableText(detection);
    }

    private boolean isFacilityPoi(Poi poi) {
        String detectType = getPoiDetectType(poi);
        return resolvePoiCategoryCode(detectType) != null && resolvePoiCategoryCode(detectType).startsWith("facility.");
    }

    private boolean isGenericPoiCandidate(Poi poi) {
        return "poi_candidate".equals(normalizeDetectType(getPoiDetectType(poi)));
    }

    private String getPoiDetectType(Poi poi) {
        Object value = poi.getAttrs() != null ? poi.getAttrs().get("detectType") : null;
        return value instanceof String stringValue ? stringValue : null;
    }

    private boolean isSameOrNearbyPoi(Poi facilityPoi, Poi genericPoi) {
        Point facilityPoint = toPoint(facilityPoi.getGeomPx());
        Point genericPoint = toPoint(genericPoi.getGeomPx());
        if (facilityPoint == null || genericPoint == null) {
            return false;
        }

        if (facilityPoint.distance(genericPoint) <= 36.0) {
            return true;
        }

        Polygon facilityFootprint = facilityPoi.getFootprintPx();
        Polygon genericFootprint = genericPoi.getFootprintPx();
        if (facilityFootprint != null && genericFootprint != null) {
            return facilityFootprint.intersects(genericFootprint)
                    || facilityFootprint.buffer(4).intersects(genericFootprint)
                    || genericFootprint.buffer(4).intersects(facilityFootprint);
        }

        if (facilityFootprint != null) {
            return facilityFootprint.buffer(4).contains(genericPoint);
        }

        if (genericFootprint != null) {
            return genericFootprint.buffer(4).contains(facilityPoint);
        }

        return false;
    }

    private String resolveNodeKindCode(String detectType) {
        return switch (normalizeDetectType(detectType)) {
            case "entrance" -> "entrance";
            case "stair" -> "stair";
            case "elevator" -> "elevator";
            case "escalator" -> "escalator";
            default -> "corridor";
        };
    }

    private Point toPoint(Geometry geometry) {
        if (geometry == null) {
            return null;
        }
        if (geometry instanceof Point point) {
            return point;
        }
        Point centroid = geometry.getCentroid();
        return centroid != null ? GEOMETRY_FACTORY.createPoint(centroid.getCoordinate()) : null;
    }

    private LineString toLineString(Geometry geometry) {
        if (geometry instanceof LineString lineString) {
            return lineString;
        }
        return null;
    }

    private String toNodeCacheKey(Point point) {
        return toNodeCacheKey(point.getCoordinate());
    }

    private String toNodeCacheKey(Coordinate coordinate) {
        return "%.3f:%.3f".formatted(coordinate.x, coordinate.y);
    }

    private UUID extractAiDetectionId(Zone zone) {
        Object value = zone.getProperties().get("aiDetectionId");
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return UUID.fromString(stringValue);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }
}
