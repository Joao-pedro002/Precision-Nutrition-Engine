package com.precisionnutrition.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record CreateMealRequest(
        UUID userId,

        @NotBlank(message = "Nome da refeição é obrigatório")
        @Size(max = 100, message = "Nome da refeição deve ter no máximo 100 caracteres")
        String name,

        @JsonAlias("mealOrder")
        Integer orderIndex,

        @JsonAlias("scheduledTime")
        LocalTime targetTime,

        List<MealItemRequest> items
) {}
