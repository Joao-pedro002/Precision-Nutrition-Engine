package com.precisionnutrition.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record WeightHistoryResponse(
        BigDecimal latestWeightKg,
        LocalDate latestLogDate,
        BigDecimal previousWeightKg,
        BigDecimal deltaKg,
        List<WeightLogEntryDto> history
) {}
