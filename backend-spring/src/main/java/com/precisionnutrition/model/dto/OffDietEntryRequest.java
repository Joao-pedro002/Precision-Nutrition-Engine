package com.precisionnutrition.model.dto;

import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record OffDietEntryRequest(
        UUID foodId,
        String customName,

        @Positive(message = "Peso consumido deve ser positivo")
        BigDecimal weightConsumedG,

        BigDecimal energyKcal,
        BigDecimal proteinG,
        BigDecimal carbohydrateG,
        BigDecimal lipidG,
        BigDecimal fiberG,
        UUID userId
) {}
