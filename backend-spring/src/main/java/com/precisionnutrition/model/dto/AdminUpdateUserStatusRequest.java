package com.precisionnutrition.model.dto;

import jakarta.validation.constraints.NotNull;

public record AdminUpdateUserStatusRequest(
        @NotNull(message = "O campo 'active' é obrigatório")
        Boolean active
) {}
