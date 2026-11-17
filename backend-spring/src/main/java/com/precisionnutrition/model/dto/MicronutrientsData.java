package com.precisionnutrition.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Contrato estrito para o objeto JSONB de micronutrientes.
 * Reflete 100% o esquema acordado em 01_Data_Contracts_DDL_JSONB.md.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record MicronutrientsData(
        @JsonProperty("calcium_mg") BigDecimal calciumMg,
        @JsonProperty("iron_mg") BigDecimal ironMg,
        @JsonProperty("magnesium_mg") BigDecimal magnesiumMg,
        @JsonProperty("potassium_mg") BigDecimal potassiumMg,
        @JsonProperty("sodium_mg") BigDecimal sodiumMg,
        @JsonProperty("zinc_mg") BigDecimal zincMg,
        @JsonProperty("vitamin_c_mg") BigDecimal vitaminCMg,
        @JsonProperty("vitamin_b12_mcg") BigDecimal vitaminB12Mcg,
        @JsonProperty("cholesterol_mg") BigDecimal cholesterolMg
) implements Serializable {

    public static MicronutrientsData empty() {
        return new MicronutrientsData(
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        );
    }

    public BigDecimal safeCalcium() { return calciumMg != null ? calciumMg : BigDecimal.ZERO; }
    public BigDecimal safeIron() { return ironMg != null ? ironMg : BigDecimal.ZERO; }
    public BigDecimal safeMagnesium() { return magnesiumMg != null ? magnesiumMg : BigDecimal.ZERO; }
    public BigDecimal safePotassium() { return potassiumMg != null ? potassiumMg : BigDecimal.ZERO; }
    public BigDecimal safeSodium() { return sodiumMg != null ? sodiumMg : BigDecimal.ZERO; }
    public BigDecimal safeZinc() { return zincMg != null ? zincMg : BigDecimal.ZERO; }
    public BigDecimal safeVitaminC() { return vitaminCMg != null ? vitaminCMg : BigDecimal.ZERO; }
    public BigDecimal safeVitaminB12() { return vitaminB12Mcg != null ? vitaminB12Mcg : BigDecimal.ZERO; }
    public BigDecimal safeCholesterol() { return cholesterolMg != null ? cholesterolMg : BigDecimal.ZERO; }
}
