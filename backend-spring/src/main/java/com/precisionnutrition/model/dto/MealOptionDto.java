package com.precisionnutrition.model.dto;

import java.math.BigDecimal;
import java.util.List;

public record MealOptionDto(
        Integer optionNumber,
        BigDecimal totalEnergyKcal,
        BigDecimal totalProteinG,
        BigDecimal totalCarbohydrateG,
        BigDecimal totalLipidG,
        BigDecimal totalFiberG,
        List<MealItemCalculatedDto> items
) {}
