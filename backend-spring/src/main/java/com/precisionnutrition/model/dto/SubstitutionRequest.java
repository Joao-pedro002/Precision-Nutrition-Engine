package com.precisionnutrition.model.dto;

import com.precisionnutrition.model.enums.MacroTarget;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record SubstitutionRequest(
        @NotNull(message = "ID do alimento de origem é obrigatório")
        UUID sourceFoodId,

        @NotNull(message = "Peso do alimento de origem é obrigatório")
        @Positive(message = "Peso do alimento de origem deve ser positivo")
        BigDecimal sourceWeightG,

        @NotNull(message = "ID do alimento alvo é obrigatório")
        UUID targetFoodId,

        MacroTarget targetMacroOverride
) {}
