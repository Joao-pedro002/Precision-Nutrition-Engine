package com.precisionnutrition.service;

import com.precisionnutrition.exception.FoodNotFoundException;
import com.precisionnutrition.model.dto.CreateMealRequest;
import com.precisionnutrition.model.dto.MealItemRequest;
import com.precisionnutrition.model.dto.MealRequest;
import com.precisionnutrition.model.entity.Food;
import com.precisionnutrition.model.entity.Meal;
import com.precisionnutrition.model.entity.MealItem;
import com.precisionnutrition.repository.FoodRepository;
import com.precisionnutrition.repository.MealItemRepository;
import com.precisionnutrition.repository.MealRepository;
import com.precisionnutrition.security.AuthenticatedUser;
import com.precisionnutrition.util.AppConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
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
        Meal meal = mealRepository.findById(mealId)
                .orElseThrow(() -> new IllegalArgumentException("Refeição não encontrada para ID: " + mealId));
        assertMealOwnership(meal);
        return meal;
    }

    @Override
    @Transactional
    public Meal createMeal(MealRequest request) {
        return createMeal(new CreateMealRequest(
                request.userId(),
                request.name(),
                request.orderIndex(),
                request.targetTime(),
                request.items()
        ));
    }

    @Override
    @Transactional
    public Meal createMeal(CreateMealRequest request) {
        UUID effectiveUser = (request.userId() != null) ? request.userId() : AppConstants.DEFAULT_USER_ID;

        int resolvedOrderIndex = (request.orderIndex() != null)
                ? request.orderIndex()
                : mealRepository.findByUserIdOrderByOrderIndexAsc(effectiveUser).stream()
                        .mapToInt(m -> m.getOrderIndex() != null ? m.getOrderIndex() : 0)
                        .max()
                        .orElse(0) + 1;

        Meal meal = Meal.builder()
                .userId(effectiveUser)
                .name(request.name().trim())
                .orderIndex(resolvedOrderIndex)
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
        assertMealOwnership(item.getMeal());

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
        assertMealOwnership(item.getMeal());
        mealItemRepository.delete(item);
    }

    private void assertMealOwnership(Meal meal) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AuthenticatedUser user) {
            if (meal != null && meal.getUserId() != null && !meal.getUserId().equals(user.id())) {
                throw new AccessDeniedException("Você não tem permissão para acessar ou modificar esta refeição.");
            }
        }
    }

    @Override
    @Transactional
    public List<Meal> generateBaseMealsIfEmpty(UUID userId) {
        UUID effectiveUser = (userId != null) ? userId : AppConstants.DEFAULT_USER_ID;

        List<Meal> existing = mealRepository.findByUserIdOrderByOrderIndexAsc(effectiveUser);
        if (!existing.isEmpty()) {
            log.info("Usuário {} já possui {} refeição(ões). Geração de estrutura base ignorada.", effectiveUser, existing.size());
            return Collections.emptyList();
        }

        List<Meal> createdMeals = new ArrayList<>();

        // 1. Café da Manhã (07:00)
        Meal cafe = saveBaseMeal(effectiveUser, "Café da Manhã", 1, LocalTime.of(7, 0));
        addBaseItemIfFound(cafe, "Ovo, de galinha, inteiro, cozido", new BigDecimal("100.00"));
        addBaseItemIfFound(cafe, "Aveia, flocos, crua", new BigDecimal("40.00"));
        addBaseItemIfFound(cafe, "Banana, prata, crua", new BigDecimal("100.00"));
        createdMeals.add(cafe);

        // 2. Almoço (12:30)
        Meal almoco = saveBaseMeal(effectiveUser, "Almoço", 2, LocalTime.of(12, 30));
        addBaseItemIfFound(almoco, "Frango, peito, sem pele, grelhado", new BigDecimal("150.00"));
        addBaseItemIfFound(almoco, "Arroz, tipo 1, cozido", new BigDecimal("150.00"));
        addBaseItemIfFound(almoco, "Feijão, carioca, cozido", new BigDecimal("100.00"));
        addBaseItemIfFound(almoco, "Azeite, de oliva, extravirgem", new BigDecimal("8.00"));
        createdMeals.add(almoco);

        // 3. Lanche da Tarde (16:00)
        Meal lanche = saveBaseMeal(effectiveUser, "Lanche da Tarde", 3, LocalTime.of(16, 0));
        addBaseItemIfFound(lanche, "Tapioca, goma / massa fresca", new BigDecimal("60.00"));
        addBaseItemIfFound(lanche, "Ovo, de galinha, inteiro, cozido", new BigDecimal("100.00"));
        createdMeals.add(lanche);

        // 4. Jantar (20:00)
        Meal jantar = saveBaseMeal(effectiveUser, "Jantar", 4, LocalTime.of(20, 0));
        addBaseItemIfFound(jantar, "Frango, peito, sem pele, grelhado", new BigDecimal("150.00"));
        addBaseItemIfFound(jantar, "Batata, doce, cozida", new BigDecimal("150.00"));
        addBaseItemIfFound(jantar, "Azeite, de oliva, extravirgem", new BigDecimal("8.00"));
        createdMeals.add(jantar);

        log.info("Estrutura base de {} refeições gerada automaticamente para usuário {}.", createdMeals.size(), effectiveUser);
        return createdMeals;
    }

    private Meal saveBaseMeal(UUID userId, String name, int orderIndex, LocalTime targetTime) {
        Meal meal = Meal.builder()
                .userId(userId)
                .name(name)
                .orderIndex(orderIndex)
                .targetTime(targetTime)
                .items(new ArrayList<>())
                .build();
        return mealRepository.save(meal);
    }

    private void addBaseItemIfFound(Meal meal, String foodNameQuery, BigDecimal weightG) {
        Optional<Food> foodOpt = foodRepository.searchByName(foodNameQuery, PageRequest.of(0, 1))
                .getContent()
                .stream()
                .findFirst();

        foodOpt.ifPresent(food -> {
            MealItem item = MealItem.builder()
                    .meal(meal)
                    .food(food)
                    .optionNumber(1)
                    .weightG(weightG.setScale(2, RoundingMode.HALF_UP))
                    .build();
            mealItemRepository.save(item);
        });
    }
}
