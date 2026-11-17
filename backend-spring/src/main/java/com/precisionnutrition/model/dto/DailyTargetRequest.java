package com.precisionnutrition.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

public record DailyTargetRequest(
        UUID userId,
        @NotNull(message = "Piso calórico é obrigatório")
        @PositiveOrZero
        BigDecimal targetKcalMin,

        @NotNull(message = "Teto do déficit calórico é obrigatório")
        @PositiveOrZero
        BigDecimal targetKcalMax,

        @NotNull(message = "Teto de manutenção calórica é obrigatório")
        @PositiveOrZero
        BigDecimal maintenanceKcalCeiling,

        @NotNull(message = "Meta de proteína é obrigatória")
        @PositiveOrZero
        BigDecimal targetProteinG,

        @NotNull(message = "Meta de carboidrato é obrigatória")
        @PositiveOrZero
        BigDecimal targetCarbohydrateG,

        @NotNull(message = "Meta de gordura é obrigatória")
        @PositiveOrZero
        BigDecimal targetLipidG,

        @PositiveOrZero
        BigDecimal targetFiberG,

        @PositiveOrZero
        BigDecimal targetWaterMl,

        MicronutrientsData targetMicronutrients,
        Boolean isActive
) {}
