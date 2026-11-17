package com.precisionnutrition.model.dto;

import java.math.BigDecimal;
import java.util.List;

public record MicronutrientMatrixDto(
        BigDecimal sodiumPotassiumRatio,
        String sodiumPotassiumStatus,
        List<MicronutrientComparisonDto> comparisons
) {}
