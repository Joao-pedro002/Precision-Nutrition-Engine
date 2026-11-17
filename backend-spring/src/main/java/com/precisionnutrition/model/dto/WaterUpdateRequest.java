package com.precisionnutrition.model.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record WaterUpdateRequest(
        @NotNull(message = "Modo de atualização (INCREMENT ou SET) é obrigatório")
        String mode,

        @NotNull(message = "Quantidade de água em ml é obrigatória")
        BigDecimal amountMl,

        UUID userId
) {}
