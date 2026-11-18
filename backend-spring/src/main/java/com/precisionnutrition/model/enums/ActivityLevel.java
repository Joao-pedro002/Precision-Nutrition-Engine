package com.precisionnutrition.model.enums;

import java.math.BigDecimal;

public enum ActivityLevel {
    SEDENTARY(new BigDecimal("1.200")),
    LIGHT(new BigDecimal("1.375")),
    MODERATE(new BigDecimal("1.550")),
    VERY_ACTIVE(new BigDecimal("1.725")),
    EXTRA_ACTIVE(new BigDecimal("1.900"));

    private final BigDecimal multiplier;

    ActivityLevel(BigDecimal multiplier) {
        this.multiplier = multiplier;
    }

    public BigDecimal getMultiplier() {
        return multiplier;
    }
}
