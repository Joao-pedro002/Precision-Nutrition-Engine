package com.precisionnutrition.repository;

import com.precisionnutrition.model.entity.DailyTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailyTargetRepository extends JpaRepository<DailyTarget, UUID> {

    Optional<DailyTarget> findByUserIdAndIsActiveTrue(UUID userId);

    List<DailyTarget> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
