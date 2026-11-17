package com.precisionnutrition.model.dto;

import java.math.BigDecimal;

public record NutrientAuditResult(
        BigDecimal officialKcal,
        BigDecimal atwaterKcal,
        BigDecimal divergencePercent,
        boolean atwaterDivergence,
        String auditNotes
) {}
