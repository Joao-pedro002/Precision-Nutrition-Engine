package com.precisionnutrition.model.dto;

import com.precisionnutrition.model.entity.DailyTarget;

import java.time.LocalDate;
import java.util.List;

public record DashboardResponse(
        LocalDate date,
        DailyTarget activeTarget,
        DailySummaryDto summary,
        MicronutrientMatrixDto micronutrients,
        List<MealDashboardDto> meals,
        List<DailyLogEntryDto> loggedEntries
) {}
