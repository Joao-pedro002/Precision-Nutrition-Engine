package com.precisionnutrition.model.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record FoodPortionDto(
        UUID id,
        String portionName,
        BigDecimal weightG
) {}
