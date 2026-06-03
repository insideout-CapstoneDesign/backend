package com.insideout.backend.domain.map.repository;

import com.insideout.backend.domain.map.entity.PoiCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PoiCategoryRepository extends JpaRepository<PoiCategory, Long> {

    Optional<PoiCategory> findByCode(String code);

    List<PoiCategory> findByCodeIn(Collection<String> codes);
}
