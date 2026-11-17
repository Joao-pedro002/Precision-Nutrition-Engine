package com.precisionnutrition.repository;

import com.precisionnutrition.model.entity.MealItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MealItemRepository extends JpaRepository<MealItem, UUID> {

    List<MealItem> findByMealIdOrderByOptionNumberAsc(UUID mealId);

    List<MealItem> findByMealIdAndOptionNumber(UUID mealId, Integer optionNumber);
}
