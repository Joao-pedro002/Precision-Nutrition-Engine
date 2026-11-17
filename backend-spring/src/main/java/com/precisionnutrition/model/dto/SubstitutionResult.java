package com.precisionnutrition.model.dto;

import com.precisionnutrition.model.enums.MacroTarget;

import java.math.BigDecimal;

public record SubstitutionResult(
        FoodSummary sourceFood,
        FoodSummary targetFood,
        BigDecimal deltaKcal,
        BigDecimal deltaProteinG,
        BigDecimal deltaCarbohydrateG,
        BigDecimal deltaLipidG,
        BigDecimal calorieDriftPercent,
        boolean calorieDriftWarning,
        String warningMessage,
        MacroTarget matchedMacro
) {}
