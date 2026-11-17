package com.precisionnutrition.model.dto;

import java.math.BigDecimal;

public record DailySummaryDto(
        BigDecimal consumedEnergyKcal,
        BigDecimal targetKcalMin,
        BigDecimal targetKcalMax,
        BigDecimal maintenanceKcalCeiling,
        CaloricZoneResult caloricZone,
        BigDecimal consumedProteinG,
        BigDecimal targetProteinG,
        BigDecimal proteinPercentage,
        BigDecimal consumedCarbohydrateG,
        BigDecimal targetCarbohydrateG,
        BigDecimal carbohydratePercentage,
        BigDecimal consumedLipidG,
        BigDecimal targetLipidG,
        BigDecimal lipidPercentage,
        BigDecimal consumedFiberG,
        BigDecimal targetFiberG,
        BigDecimal waterConsumedMl,
        BigDecimal targetWaterMl,
        BigDecimal waterPercentage
) {}
