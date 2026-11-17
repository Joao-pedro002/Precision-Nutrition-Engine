package com.precisionnutrition.model.dto;

import com.precisionnutrition.model.enums.CalorieZone;

import java.math.BigDecimal;

public record CaloricZoneResult(
        BigDecimal currentKcal,
        BigDecimal targetKcalMin,
        BigDecimal targetKcalMax,
        BigDecimal maintenanceKcalCeiling,
        CalorieZone zone,
        BigDecimal deltaToTargetMin,
        BigDecimal deltaToMaintenanceCeiling
) {}
