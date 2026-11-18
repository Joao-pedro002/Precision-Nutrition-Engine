package com.precisionnutrition.model.dto;

import com.precisionnutrition.model.enums.ActivityLevel;
import com.precisionnutrition.model.enums.DietGoal;
import com.precisionnutrition.model.enums.Gender;
import com.precisionnutrition.model.enums.TargetSource;

import java.math.BigDecimal;

public record TargetCalculationResponse(
        Gender gender,
        Integer ageYears,
        BigDecimal weightKg,
        BigDecimal heightCm,
        ActivityLevel activityLevel,
        DietGoal goal,
        BigDecimal bmrKcal,
        BigDecimal tdeeKcal,
        BigDecimal targetKcalMin,
        BigDecimal targetKcalMax,
        BigDecimal maintenanceKcalCeiling,
        BigDecimal targetProteinG,
        BigDecimal targetCarbohydrateG,
        BigDecimal targetLipidG,
        BigDecimal targetFiberG,
        BigDecimal targetWaterMl,
        MicronutrientsData targetMicronutrients,
        TargetSource targetSource,
        BigDecimal proteinMultiplier
) {}
