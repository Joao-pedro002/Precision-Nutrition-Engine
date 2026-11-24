package com.precisionnutrition.service;

import com.precisionnutrition.exception.FoodNotFoundException;
import com.precisionnutrition.model.dto.MealItemRequest;
import com.precisionnutrition.model.dto.MealRequest;
import com.precisionnutrition.model.entity.Food;
import com.precisionnutrition.model.entity.Meal;
import com.precisionnutrition.model.entity.MealItem;
import com.precisionnutrition.repository.FoodRepository;
import com.precisionnutrition.repository.MealItemRepository;
import com.precisionnutrition.repository.MealRepository;
import com.precisionnutrition.util.AppConstants;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MealServiceImpl implements MealService {

    private final MealRepository mealRepository;
    private final MealItemRepository mealItemRepository;
    private final FoodRepository foodRepository;

    @Override
    @Transactional(readOnly = true)
    public List<Meal> getUserMeals(UUID userId) {
        UUID effectiveUser = (userId != null) ? userId : AppConstants.DEFAULT_USER_ID;
        return mealRepository.findByUserIdOrderByOrderIndexAsc(effectiveUser);
    }

    @Override
    @Transactional(readOnly = true)
    public Meal getMealById(UUID mealId) {
        return mealRepository.findById(mealId)
                .orElseThrow(() -> new IllegalArgumentException("Refeição não encontrada para ID: " + mealId));
    }

    @Override
    @Transactional
    public Meal createMeal(MealRequest request) {
        UUID effectiveUser = (request.userId() != null) ? request.userId() : AppConstants.DEFAULT_USER_ID;

        Meal meal = Meal.builder()
                .userId(effectiveUser)
                .name(request.name().trim())
                .orderIndex(request.orderIndex() != null ? request.orderIndex() : 0)
                .targetTime(request.targetTime())
                .items(new ArrayList<>())
                .build();

        Meal savedMeal = mealRepository.save(meal);

        if (request.items() != null && !request.items().isEmpty()) {
            for (MealItemRequest itemReq : request.items()) {
                Food food = foodRepository.findById(itemReq.foodId())
                        .orElseThrow(() -> new FoodNotFoundException(itemReq.foodId()));

                MealItem item = MealItem.builder()
                        .meal(savedMeal)
                        .food(food)
                        .optionNumber(itemReq.optionNumber() != null ? itemReq.optionNumber() : 1)
                        .weightG(itemReq.weightG().setScale(2, RoundingMode.HALF_UP))
                        .build();

                savedMeal.getItems().add(item);
            }
            savedMeal = mealRepository.save(savedMeal);
        }

        return savedMeal;
    }

    @Override
    @Transactional
    public Meal updateMeal(UUID mealId, MealRequest request) {
        Meal meal = getMealById(mealId);

        meal.setName(request.name().trim());
        if (request.orderIndex() != null) {
            meal.setOrderIndex(request.orderIndex());
        }
        if (request.targetTime() != null) {
            meal.setTargetTime(request.targetTime());
        }

        return mealRepository.save(meal);
    }

    @Override
    @Transactional
    public void deleteMeal(UUID mealId) {
        Meal meal = getMealById(mealId);
        mealRepository.delete(meal);
    }

    @Override
    @Transactional
    public MealItem addItemToMeal(UUID mealId, MealItemRequest request) {
        Meal meal = getMealById(mealId);
        Food food = foodRepository.findById(request.foodId())
                .orElseThrow(() -> new FoodNotFoundException(request.foodId()));

        MealItem item = MealItem.builder()
                .meal(meal)
                .food(food)
                .optionNumber(request.optionNumber() != null ? request.optionNumber() : 1)
                .weightG(request.weightG().setScale(2, RoundingMode.HALF_UP))
                .build();

        MealItem saved = mealItemRepository.save(item);
        Hibernate.initialize(saved.getFood());
        return saved;
    }

    @Override
    @Transactional
    public MealItem updateMealItemWeight(UUID itemId, BigDecimal weightG) {
        MealItem item = mealItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item de refeição não encontrado para ID: " + itemId));

        item.setWeightG(weightG.setScale(2, RoundingMode.HALF_UP));
        MealItem saved = mealItemRepository.save(item);
        Hibernate.initialize(saved.getFood());
        return saved;
    }

    @Override
    @Transactional
    public void removeItemFromMeal(UUID itemId) {
        MealItem item = mealItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item de refeição não encontrado para ID: " + itemId));
        mealItemRepository.delete(item);
    }
}
