package com.precisionnutrition.repository;

import com.precisionnutrition.model.entity.Meal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MealRepository extends JpaRepository<Meal, UUID> {

    List<Meal> findByUserIdOrderByOrderIndexAsc(UUID userId);
}
