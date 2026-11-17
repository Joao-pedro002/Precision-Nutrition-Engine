package com.precisionnutrition.model.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DailyLogEntryDto(
        UUID id,
        UUID mealId,
        UUID foodId,
        String foodNameSnapshot,
        BigDecimal weightConsumedG,
        BigDecimal consumedEnergyKcal,
        BigDecimal consumedProteinG,
        BigDecimal consumedCarbohydrateG,
        BigDecimal consumedLipidG,
        BigDecimal consumedFiberG,
        MicronutrientsData consumedMicronutrients,
        boolean isOffDiet,
        OffsetDateTime loggedAt
) {}
