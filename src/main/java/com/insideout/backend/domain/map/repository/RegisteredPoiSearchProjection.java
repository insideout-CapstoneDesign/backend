package com.insideout.backend.domain.map.repository;

public interface RegisteredPoiSearchProjection {
    java.util.UUID getBuildingId();

    java.util.UUID getPoiId();

    String getName();

    String getAddress();

    String getBuildingName();

    String getExternalApiId();
}
