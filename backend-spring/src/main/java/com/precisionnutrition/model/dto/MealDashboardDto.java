package com.precisionnutrition.model.dto;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record MealDashboardDto(
        UUID id,
        String name,
        Integer orderIndex,
        LocalTime targetTime,
        boolean isCompleted,
        Integer completedOptionNumber,
        List<MealOptionDto> options
) {}
