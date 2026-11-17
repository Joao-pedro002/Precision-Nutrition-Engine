package com.precisionnutrition.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DailyLogResponse(
        UUID id,
        UUID userId,
        LocalDate date,
        BigDecimal waterConsumedMl,
        BigDecimal waterTargetMl,
        BigDecimal waterPercent,
        DailySummaryDto summary,
        List<DailyLogEntryDto> entries
) {}
