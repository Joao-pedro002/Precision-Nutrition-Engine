package com.precisionnutrition.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UsdaNutrient(
        @JsonProperty("nutrientNumber") String nutrientNumber,
        @JsonProperty("nutrientName") String nutrientName,
        @JsonProperty("value") BigDecimal value,
        @JsonProperty("unitName") String unitName
) {}
