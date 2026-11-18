package com.precisionnutrition.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UsdaFoodItem(
        @JsonProperty("fdcId") Long fdcId,
        @JsonProperty("description") String description,
        @JsonProperty("brandOwner") String brandOwner,
        @JsonProperty("brandName") String brandName,
        @JsonProperty("foodCategory") String foodCategory,
        @JsonProperty("dataType") String dataType,
        @JsonProperty("foodNutrients") List<UsdaNutrient> foodNutrients
) {
    public List<UsdaNutrient> safeNutrients() {
        return foodNutrients != null ? foodNutrients : Collections.emptyList();
    }
}
