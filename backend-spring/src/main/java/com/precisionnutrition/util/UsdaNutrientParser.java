package com.precisionnutrition.util;

import com.precisionnutrition.model.dto.UsdaNutrient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;

/**
 * Parser defensivo de nutrientes da USDA FoodData Central.
 * Impede que valores em kJ (quilojoules) sejam gravados ou sobrescrevam energia em kcal.
 */
public final class UsdaNutrientParser {

    private static final BigDecimal KJ_TO_KCAL_DIVISOR = new BigDecimal("4.184");
    private static final Set<Integer> PRIMARY_KCAL_IDS = Set.of(1008, 208);
    private static final Set<Integer> SECONDARY_KCAL_IDS = Set.of(2047, 2048);
    private static final Set<Integer> KJ_IDS = Set.of(1062, 268);

    private UsdaNutrientParser() {}

    /**
     * Extrai energia em kcal da lista de nutrientes da USDA:
     * 1. Prioriza exclusivamente entradas com unitName == "KCAL" (ID 1008/208 primeiro, depois 2047/2048 ou nome "Energy").
     * 2. Apenas se não houver nenhuma entrada em KCAL (> 0), converte explicitamente kJ dividindo por 4.184.
     * 3. Como último recurso sem unidade declarada, aceita apenas ID 1008/208 (ou número "208").
     */
    public static BigDecimal extractEnergyKcal(List<UsdaNutrient> nutrients) {
        if (nutrients == null || nutrients.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal primaryKcal = null;
        BigDecimal secondaryKcal = null;
        BigDecimal kjFallback = null;
        BigDecimal unitlessKcalFallback = null;

        for (UsdaNutrient n : nutrients) {
            if (n == null || n.value() == null || n.value().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            if (!isEnergyNutrient(n)) {
                continue;
            }

            String unit = n.unitName() != null ? n.unitName().trim() : "";
            Integer id = n.nutrientId();
            String num = n.nutrientNumber() != null ? n.nutrientNumber().trim() : "";

            if ("KCAL".equalsIgnoreCase(unit)) {
                if ((id != null && PRIMARY_KCAL_IDS.contains(id)) || "208".equals(num)) {
                    if (primaryKcal == null) {
                        primaryKcal = n.value();
                    }
                } else if (secondaryKcal == null) {
                    secondaryKcal = n.value();
                }
            } else if ("KJ".equalsIgnoreCase(unit)) {
                if (kjFallback == null) {
                    kjFallback = n.value();
                }
            } else if (unit.isEmpty()) {
                if ((id != null && PRIMARY_KCAL_IDS.contains(id)) || "208".equals(num)) {
                    if (unitlessKcalFallback == null) {
                        unitlessKcalFallback = n.value();
                    }
                }
            }
        }

        if (primaryKcal != null) {
            return primaryKcal.setScale(2, RoundingMode.HALF_UP);
        }
        if (secondaryKcal != null) {
            return secondaryKcal.setScale(2, RoundingMode.HALF_UP);
        }
        if (kjFallback != null) {
            return kjFallback.divide(KJ_TO_KCAL_DIVISOR, 2, RoundingMode.HALF_UP);
        }
        if (unitlessKcalFallback != null) {
            return unitlessKcalFallback.setScale(2, RoundingMode.HALF_UP);
        }

        return BigDecimal.ZERO;
    }

    private static boolean isEnergyNutrient(UsdaNutrient n) {
        Integer id = n.nutrientId();
        String num = n.nutrientNumber() != null ? n.nutrientNumber().trim() : "";
        String name = n.nutrientName() != null ? n.nutrientName().toLowerCase() : "";

        boolean idMatch = id != null && (
                PRIMARY_KCAL_IDS.contains(id)
                        || SECONDARY_KCAL_IDS.contains(id)
                        || KJ_IDS.contains(id)
        );
        boolean numberMatch = "208".equals(num) || "268".equals(num) || "957".equals(num) || "958".equals(num);
        boolean nameMatch = name.contains("energy");

        return idMatch || numberMatch || nameMatch;
    }
}
