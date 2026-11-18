package com.precisionnutrition.service;

import com.precisionnutrition.model.dto.UsdaSearchResponse;

public interface UsdaApiClient {

    /**
     * Executa chamada HTTP GET para https://api.nal.usda.gov/fdc/v1/foods/search?query={query}&api_key={key}
     */
    UsdaSearchResponse searchFoods(String query);

    UsdaSearchResponse searchFoods(String query, int pageSize);
}
