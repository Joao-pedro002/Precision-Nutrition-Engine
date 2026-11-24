package com.precisionnutrition.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateMealItemWeightRequest(
        @JsonAlias("quantityG")
        @NotNull(message = "A quantidade em gramas é obrigatória")
        @DecimalMin(value = "0.1", message = "A quantidade em gramas deve ser maior que zero")
        BigDecimal weightG
) {}
