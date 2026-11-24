package com.precisionnutrition.model.dto;

import com.precisionnutrition.model.enums.DominantMacro;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CustomFoodRequest(
        @NotBlank(message = "Nome do alimento é obrigatório")
        String name,

        String category,
        DominantMacro dominantMacro,

        @NotNull(message = "Calorias por 100g são obrigatórias")
        @DecimalMin(value = "0.0", message = "Calorias não podem ser negativas")
        BigDecimal energyKcal,

        @NotNull(message = "Proteínas por 100g são obrigatórias")
        @DecimalMin(value = "0.0", message = "Proteínas não podem ser negativas")
        BigDecimal proteinG,

        @NotNull(message = "Carboidratos por 100g são obrigatórios")
        @DecimalMin(value = "0.0", message = "Carboidratos não podem ser negativos")
        BigDecimal carbohydrateG,

        @NotNull(message = "Gorduras por 100g são obrigatórias")
        @DecimalMin(value = "0.0", message = "Gorduras não podem ser negativas")
        BigDecimal lipidG,

        @DecimalMin(value = "0.0", message = "Fibras não podem ser negativas")
        BigDecimal fiberG,

        MicronutrientsData micronutrients
) {}
