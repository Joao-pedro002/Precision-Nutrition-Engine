package com.precisionnutrition.repository;

import com.precisionnutrition.model.entity.Food;
import com.precisionnutrition.model.enums.DominantMacro;
import com.precisionnutrition.model.enums.FoodSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
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
            OR similarity(immutable_unaccent(name), immutable_unaccent(:query)) > 0.25
          )
        ORDER BY
          CASE WHEN immutable_unaccent(name) ILIKE CONCAT('%', immutable_unaccent(:query), '%') THEN 0 ELSE 1 END,
          popularity_score DESC,
          similarity(immutable_unaccent(name), immutable_unaccent(:query)) DESC,
          name ASC
        """,
        countQuery = """
        SELECT count(*) FROM foods
        WHERE (:source IS NULL OR source = :source)
          AND (:dominantMacro IS NULL OR dominant_macro = :dominantMacro)
          AND (
            immutable_unaccent(name) ILIKE CONCAT('%', immutable_unaccent(:query), '%')
            OR similarity(immutable_unaccent(name), immutable_unaccent(:query)) > 0.25
          )
        """,
        nativeQuery = true)
    Page<Food> searchFuzzy(
            @Param("query") String query,
            @Param("source") String source,
            @Param("dominantMacro") String dominantMacro,
            Pageable pageable
    );

    @Query("SELECT f FROM Food f LEFT JOIN FETCH f.portions WHERE f.foodGroup = :foodGroup AND f.id != :excludedId ORDER BY f.popularityScore DESC, f.name ASC")
    List<Food> findCandidatesByFoodGroup(@Param("foodGroup") String foodGroup, @Param("excludedId") UUID excludedId, Pageable pageable);

    @Query("SELECT f FROM Food f LEFT JOIN FETCH f.portions WHERE f.dominantMacro = :dominantMacro AND f.id != :excludedId ORDER BY f.popularityScore DESC, f.name ASC")
    List<Food> findCandidatesByDominantMacro(@Param("dominantMacro") DominantMacro dominantMacro, @Param("excludedId") UUID excludedId, Pageable pageable);

    default Page<Food> searchByName(String query, Pageable pageable) {
        return searchFuzzy(query, null, null, pageable);
    }
}
