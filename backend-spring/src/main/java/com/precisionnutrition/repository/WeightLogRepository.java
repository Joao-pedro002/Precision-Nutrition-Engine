package com.precisionnutrition.repository;

import com.precisionnutrition.model.entity.WeightLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WeightLogRepository extends JpaRepository<WeightLog, UUID> {

    Optional<WeightLog> findByUserIdAndLogDate(UUID userId, LocalDate logDate);

    List<WeightLog> findTop30ByUserIdOrderByLogDateDesc(UUID userId);

    List<WeightLog> findTop30ByUserIdAndLogDateLessThanEqualOrderByLogDateDesc(UUID userId, LocalDate maxDate);
}
