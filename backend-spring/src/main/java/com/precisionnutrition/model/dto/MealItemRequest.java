package com.precisionnutrition.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record MealItemRequest(
        @NotNull(message = "ID do alimento é obrigatório")
        UUID foodId,

        @NotNull(message = "Número da opção é obrigatório")
        @Positive
        Integer optionNumber,

        @NotNull(message = "Peso do item em gramas é obrigatório")
        @Positive(message = "Peso deve ser estritamente positivo")
        BigDecimal weightG
) {}
