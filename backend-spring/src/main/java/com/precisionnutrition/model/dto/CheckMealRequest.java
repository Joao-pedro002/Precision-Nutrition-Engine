package com.precisionnutrition.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record CheckMealRequest(
        @NotNull(message = "ID da refeição é obrigatório")
        UUID mealId,

        @NotNull(message = "Número da opção é obrigatório")
        @Positive(message = "Número da opção deve ser positivo (1, 2, ...)")
        Integer optionNumber,

        UUID userId
) {}
