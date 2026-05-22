package com.insideout.backend.domain.building.repository;

import com.insideout.backend.domain.building.entity.Floor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FloorRepository extends JpaRepository<Floor, UUID> {
    Optional<Floor> findByIdAndBuilding_Id(UUID id, UUID buildingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Floor f where f.id = :id and f.building.id = :buildingId")
    Optional<Floor> findByIdAndBuilding_IdForUpdate(@Param("id") UUID id, @Param("buildingId") UUID buildingId);

    List<Floor> findAllByBuilding_IdOrderByLevelDesc(UUID buildingId);
    List<Floor> findAllByBuilding_IdInOrderByLevelDesc(List<UUID> buildingIds);
}
