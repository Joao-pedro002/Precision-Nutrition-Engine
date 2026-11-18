package com.precisionnutrition.repository;

import com.precisionnutrition.model.entity.FoodPortion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FoodPortionRepository extends JpaRepository<FoodPortion, UUID> {

    List<FoodPortion> findByFoodId(UUID foodId);
}
