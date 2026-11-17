package com.precisionnutrition.model.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record MealItemCalculatedDto(
        UUID id,
        UUID foodId,
        String foodName,
        BigDecimal weightG,
        BigDecimal energyKcal,
        BigDecimal proteinG,
        BigDecimal carbohydrateG,
        BigDecimal lipidG,
        BigDecimal fiberG,
        MicronutrientsData micronutrients
) {}
