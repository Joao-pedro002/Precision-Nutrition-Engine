package com.precisionnutrition.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record AddMealOptionItemRequest(
        @NotNull(message = "ID do alimento é obrigatório")
        UUID foodId,

        @JsonAlias("quantityG")
        @NotNull(message = "A quantidade em gramas é obrigatória")
        @DecimalMin(value = "0.1", message = "A quantidade em gramas deve ser maior que zero")
        BigDecimal weightG
) {}
