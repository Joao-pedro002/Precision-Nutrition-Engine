package com.precisionnutrition.model.dto;

import com.precisionnutrition.model.enums.ActivityLevel;
import com.precisionnutrition.model.enums.DietGoal;
import com.precisionnutrition.model.enums.Gender;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record TargetCalculationRequest(
        UUID userId,
        @NotNull(message = "Gênero é obrigatório")
        Gender gender,
        @NotNull(message = "Idade é obrigatória")
        @Positive(message = "Idade deve ser maior que zero")
        Integer ageYears,
        @NotNull(message = "Peso é obrigatório")
        @Positive(message = "Peso deve ser maior que zero")
        BigDecimal weightKg,
        @NotNull(message = "Altura é obrigatória")
        @Positive(message = "Altura deve ser maior que zero")
        BigDecimal heightCm,
        @NotNull(message = "Nível de atividade é obrigatório")
        ActivityLevel activityLevel,
        @NotNull(message = "Objetivo da dieta é obrigatório")
        DietGoal goal,
        BigDecimal proteinMultiplier
) {
    public TargetCalculationRequest(
            UUID userId,
            Gender gender,
            Integer ageYears,
            BigDecimal weightKg,
            BigDecimal heightCm,
            ActivityLevel activityLevel,
            DietGoal goal
    ) {
        this(userId, gender, ageYears, weightKg, heightCm, activityLevel, goal, null);
    }
}
