package com.precisionnutrition.service;

import com.precisionnutrition.model.dto.CreateMealRequest;
import com.precisionnutrition.model.dto.MealItemRequest;
import com.precisionnutrition.model.dto.MealRequest;
import com.precisionnutrition.model.entity.Meal;
import com.precisionnutrition.model.entity.MealItem;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface MealService {

    List<Meal> getUserMeals(UUID userId);

    Meal getMealById(UUID mealId);

    Meal createMeal(MealRequest request);

    Meal createMeal(CreateMealRequest request);

    Meal updateMeal(UUID mealId, MealRequest request);

    void deleteMeal(UUID mealId);

    MealItem addItemToMeal(UUID mealId, MealItemRequest request);

    MealItem updateMealItemWeight(UUID itemId, BigDecimal weightG);

    void removeItemFromMeal(UUID itemId);
}
