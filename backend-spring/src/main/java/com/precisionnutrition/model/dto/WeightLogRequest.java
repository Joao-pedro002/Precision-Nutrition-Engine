package com.precisionnutrition.model.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record WeightLogRequest(
        LocalDate logDate,

        @NotNull(message = "O peso em kg é obrigatório")
        @DecimalMin(value = "20.0", message = "O peso mínimo permitido é 20.0 kg")
        @DecimalMax(value = "400.0", message = "O peso máximo permitido é 400.0 kg")
        BigDecimal weightKg,

        UUID userId
) {}
