package com.precisionnutrition.repository;

import com.precisionnutrition.model.entity.Food;
import com.precisionnutrition.model.enums.FoodSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FoodRepository extends JpaRepository<Food, UUID> {

    Optional<Food> findBySourceAndExternalId(FoodSource source, String externalId);

    @Query(value = """
        SELECT * FROM foods
        WHERE (:source IS NULL OR source = :source)
          AND (:dominantMacro IS NULL OR dominant_macro = :dominantMacro)
          AND (
            immutable_unaccent(name) ILIKE CONCAT('%', immutable_unaccent(:query), '%')
            OR similarity(name, :query) > 0.15
          )
        ORDER BY similarity(name, :query) DESC, name ASC
        """,
        countQuery = """
        SELECT count(*) FROM foods
        WHERE (:source IS NULL OR source = :source)
          AND (:dominantMacro IS NULL OR dominant_macro = :dominantMacro)
          AND (
            immutable_unaccent(name) ILIKE CONCAT('%', immutable_unaccent(:query), '%')
            OR similarity(name, :query) > 0.15
          )
        """,
        nativeQuery = true)
    Page<Food> searchFuzzy(
            @Param("query") String query,
            @Param("source") String source,
            @Param("dominantMacro") String dominantMacro,
            Pageable pageable
    );

    default Page<Food> searchByName(String query, Pageable pageable) {
        return searchFuzzy(query, null, null, pageable);
    }
}
