package com.precisionnutrition.model.dto;

import java.math.BigDecimal;

public record MicronutrientComparisonDto(
        String nutrientKey,
        String displayName,
        String unit,
        BigDecimal consumedAmount,
        BigDecimal targetAmount,
        BigDecimal percentageOfTarget
) {}
