package com.precisionnutrition.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WaterUpdateResponse(
        LocalDate date,
        BigDecimal waterConsumedMl,
        BigDecimal waterTargetMl,
        BigDecimal waterPercent
) {}
