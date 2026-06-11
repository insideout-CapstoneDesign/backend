package com.insideout.backend.domain.map.service;

import com.insideout.backend.domain.map.entity.Zone;
import com.insideout.backend.domain.map.entity.ZoneKind;
import org.locationtech.jts.geom.Polygon;

import java.util.ArrayList;
import java.util.List;

public final class ZoneDedupUtil {

    private ZoneDedupUtil() {
        // Prevent instantiation
    }

    public static List<Zone> suppressDuplicateRoomZones(List<Zone> zones) {
        List<Zone> filtered = new ArrayList<>();
        for (Zone candidate : zones) {
            int duplicateIndex = -1;
            for (int index = 0; index < filtered.size(); index++) {
                Zone existing = filtered.get(index);
                if (isDuplicateRoomZone(existing, candidate)) {
                    duplicateIndex = index;
                    if (shouldPreferRoomZone(candidate, existing)) {
                        filtered.set(index, candidate);
                    }
                    break;
                }
            }
            if (duplicateIndex < 0) {
                filtered.add(candidate);
            }
        }
        return filtered;
    }

    public static boolean isDuplicateRoomZone(Zone existing, Zone candidate) {
        if (existing.getKind() != ZoneKind.room || candidate.getKind() != ZoneKind.room) {
            return false;
        }

        String existingName = normalizeZoneName(existing.getName());
        String candidateName = normalizeZoneName(candidate.getName());
        if (existingName != null && candidateName != null && !existingName.equals(candidateName)) {
            return false;
        }

        Polygon existingPolygon = existing.getGeomPx();
        Polygon candidatePolygon = candidate.getGeomPx();
        if (existingPolygon == null || candidatePolygon == null) {
            return false;
        }

        if (!existingPolygon.intersects(candidatePolygon)) {
            return false;
        }

        double existingArea = existingPolygon.getArea();
        double candidateArea = candidatePolygon.getArea();
        double minArea = Math.min(existingArea, candidateArea);
        if (minArea <= 0.0) {
            return false;
        }

        double overlapArea = existingPolygon.intersection(candidatePolygon).getArea();
        return (overlapArea / minArea) >= 0.92;
    }

    public static boolean shouldPreferRoomZone(Zone candidate, Zone existing) {
        boolean candidateNamed = normalizeZoneName(candidate.getName()) != null;
        boolean existingNamed = normalizeZoneName(existing.getName()) != null;

        if (candidateNamed != existingNamed) {
            return candidateNamed;
        }

        return candidate.getGeomPx().getArea() > existing.getGeomPx().getArea();
    }

    public static String normalizeZoneName(String name) {
        if (name == null) {
            return null;
        }

        String trimmed = name.trim();
        return trimmed.isBlank() ? null : trimmed.toLowerCase();
    }
}
