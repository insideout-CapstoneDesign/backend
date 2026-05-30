package com.insideout.backend.domain.building.repository;

import java.util.UUID;

public interface BuildingSearchProjection {
    UUID getId();

    String getName();

    String getAddress();

    Double getLat();

    Double getLng();

    String getExternalApiId();
}
