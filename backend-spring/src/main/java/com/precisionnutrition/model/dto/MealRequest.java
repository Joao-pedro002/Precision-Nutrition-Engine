package com.precisionnutrition.model.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record MealRequest(
        UUID userId,
        @NotBlank(message = "Nome da refeição é obrigatório")
        String name,
        Integer orderIndex,
        LocalTime targetTime,
        List<MealItemRequest> items
) {}
