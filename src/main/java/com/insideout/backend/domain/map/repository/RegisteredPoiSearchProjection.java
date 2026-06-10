package com.insideout.backend.domain.map.repository;

public interface RegisteredPoiSearchProjection {
    java.util.UUID getBuildingId();

    Long getPoiId();

    String getName();

    String getAddress();

    String getBuildingName();

    String getExternalApiId();

    Double getLat();

    Double getLng();
}
