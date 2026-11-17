package com.precisionnutrition.model.dto;

import com.precisionnutrition.model.enums.DominantMacro;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record FoodCreateRequest(
        @NotBlank(message = "Nome do alimento é obrigatório")
        String name,

        String category,
        DominantMacro dominantMacro,

        @NotNull(message = "Calorias são obrigatórias")
        @PositiveOrZero
        BigDecimal energyKcal,

        @NotNull(message = "Proteínas são obrigatórias")
        @PositiveOrZero
        BigDecimal proteinG,

        @NotNull(message = "Carboidratos são obrigatórios")
        @PositiveOrZero
        BigDecimal carbohydrateG,

        @NotNull(message = "Gorduras são obrigatórias")
        @PositiveOrZero
        BigDecimal lipidG,

        @PositiveOrZero
        BigDecimal fiberG,

        MicronutrientsData micronutrients
) {}
