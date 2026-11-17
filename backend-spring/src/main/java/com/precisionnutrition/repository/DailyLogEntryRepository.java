package com.precisionnutrition.repository;

import com.precisionnutrition.model.entity.DailyLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DailyLogEntryRepository extends JpaRepository<DailyLogEntry, UUID> {

    List<DailyLogEntry> findByDailyLogIdOrderByLoggedAtAsc(UUID dailyLogId);

    List<DailyLogEntry> findByDailyLogIdAndMealId(UUID dailyLogId, UUID mealId);
}
