package com.precisionnutrition.controller;

import com.precisionnutrition.model.dto.AddMealOptionItemRequest;
import com.precisionnutrition.model.dto.CreateMealRequest;
import com.precisionnutrition.model.dto.MealItemRequest;
import com.precisionnutrition.model.dto.MealRequest;
import com.precisionnutrition.model.dto.UpdateMealItemWeightRequest;
import com.precisionnutrition.model.entity.Meal;
import com.precisionnutrition.model.entity.MealItem;
import com.precisionnutrition.service.MealService;
import com.precisionnutrition.util.AppConstants;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/meals")
@RequiredArgsConstructor
@Slf4j
public class MealController {

    private final MealService mealService;

    @GetMapping
    public ResponseEntity<List<Meal>> getUserMeals(@RequestParam(name = "userId", required = false) UUID userId) {
        UUID resolvedUserId = userId != null ? userId : AppConstants.DEFAULT_USER_ID;
        List<Meal> meals = mealService.getUserMeals(resolvedUserId);
        return ResponseEntity.ok(meals);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Meal> getMealById(@PathVariable UUID id) {
        Meal meal = mealService.getMealById(id);
        return ResponseEntity.ok(meal);
    }

    @PostMapping
    public ResponseEntity<Meal> createMeal(@Valid @RequestBody CreateMealRequest request) {
        Meal created = mealService.createMeal(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Meal> updateMeal(@PathVariable UUID id, @Valid @RequestBody MealRequest request) {
        Meal updated = mealService.updateMeal(id, request);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMeal(@PathVariable UUID id) {
        mealService.deleteMeal(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/items")
    public ResponseEntity<MealItem> addItemToMeal(@PathVariable UUID id, @Valid @RequestBody MealItemRequest request) {
        MealItem item = mealService.addItemToMeal(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(item);
    }

    @PostMapping("/{mealId}/options/{optionNumber}/items")
    public ResponseEntity<MealItem> addItemToMealOption(
            @PathVariable UUID mealId,
            @PathVariable Integer optionNumber,
            @Valid @RequestBody AddMealOptionItemRequest request
    ) {
        MealItem item = mealService.addItemToMeal(
                mealId,
                new MealItemRequest(request.foodId(), optionNumber, request.weightG())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(item);
    }

    @PatchMapping("/items/{itemId}")
    public ResponseEntity<MealItem> updateMealItemWeight(
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateMealItemWeightRequest request
    ) {
        MealItem updated = mealService.updateMealItemWeight(itemId, request.weightG());
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<Void> removeItemFromMeal(@PathVariable UUID itemId) {
        mealService.removeItemFromMeal(itemId);
        return ResponseEntity.noContent().build();
    }
}
