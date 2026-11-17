package com.precisionnutrition.model.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record FoodSummary(
        UUID id,
        String name,
        BigDecimal weightG,
        BigDecimal energyKcal,
        BigDecimal proteinG,
        BigDecimal carbohydrateG,
        BigDecimal lipidG,
        BigDecimal fiberG
) {}
