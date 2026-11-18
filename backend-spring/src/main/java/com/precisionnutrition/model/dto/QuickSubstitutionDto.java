package com.precisionnutrition.model.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record QuickSubstitutionDto(
        UUID targetFoodId,
        String targetFoodName,
        String foodGroup,
        BigDecimal calculatedWeightG,
        BigDecimal energyKcal,
        BigDecimal proteinG,
        BigDecimal carbohydrateG,
        BigDecimal lipidG,
        BigDecimal deltaKcal,
        BigDecimal deltaProteinG,
        BigDecimal deltaCarbohydrateG,
        BigDecimal deltaLipidG,
        BigDecimal calorieDriftPercent,
        boolean calorieDriftWarning,
        String warningMessage,
        List<FoodPortionDto> portions
) {}
