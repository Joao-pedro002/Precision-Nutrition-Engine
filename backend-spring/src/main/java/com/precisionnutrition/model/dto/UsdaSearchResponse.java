package com.precisionnutrition.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UsdaSearchResponse(
        @JsonProperty("totalHits") Integer totalHits,
        @JsonProperty("currentPage") Integer currentPage,
        @JsonProperty("totalPages") Integer totalPages,
        @JsonProperty("foods") List<UsdaFoodItem> foods
) {
    public List<UsdaFoodItem> safeFoods() {
        return foods != null ? foods : Collections.emptyList();
    }
}
