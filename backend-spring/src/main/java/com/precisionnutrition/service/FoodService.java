package com.precisionnutrition.service;

import com.precisionnutrition.model.dto.CustomFoodRequest;
import com.precisionnutrition.model.dto.FoodCreateRequest;
import com.precisionnutrition.model.entity.Food;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface FoodService {

    Page<Food> searchFoods(String query, String source, String dominantMacro, boolean includeUsda, Pageable pageable);

    Food getFoodById(UUID id);

    Food createCustomFood(FoodCreateRequest request);

    Food createCustomFood(CustomFoodRequest request);

    Food syncUsdaFood(Long fdcId);
}
