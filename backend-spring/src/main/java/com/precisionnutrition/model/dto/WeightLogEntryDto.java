package com.precisionnutrition.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WeightLogEntryDto(
        UUID id,
        LocalDate logDate,
        BigDecimal weightKg,
        OffsetDateTime createdAt
) {}
