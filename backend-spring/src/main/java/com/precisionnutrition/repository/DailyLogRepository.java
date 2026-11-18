package com.precisionnutrition.repository;

import com.precisionnutrition.model.entity.DailyLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailyLogRepository extends JpaRepository<DailyLog, UUID> {

    Optional<DailyLog> findByUserIdAndDate(UUID userId, LocalDate date);

    @Modifying
    @Transactional
    @Query("DELETE FROM DailyLog d WHERE d.date < :cutoffDate")
    int deleteByDateBefore(@Param("cutoffDate") LocalDate cutoffDate);
}
