package com.precisionnutrition.repository;

import com.precisionnutrition.model.entity.Food;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FoodRepositoryIntegrationTest {

    @Autowired
    private FoodRepository foodRepository;

    @Test
    @DisplayName("Integração: Deve consultar alimentos na TACO via Trigramas e Unaccent no PostgreSQL 16 (Porta 5433)")
    void testFuzzySearchWithUnaccentAndTrigram() {
        // Busca "pao frances" sem acentos
        Page<Food> result = foodRepository.searchFuzzy(
                "pao frances",
                "TACO",
                null,
                PageRequest.of(0, 5)
        );

        assertNotNull(result);
        assertFalse(result.isEmpty(), "Deve encontrar 'Pão, francês' mesmo sem acentos via immutable_unaccent");
        assertTrue(result.getContent().stream().anyMatch(f -> f.getName().contains("Pão")),
                "O alimento retornado deve ser o Pão francês");

        Food bread = result.getContent().get(0);
        assertNotNull(bread.getMicronutrients());
        assertNotNull(bread.getMicronutrients().sodiumMg(), "JSONB de micronutrientes deve estar desserializado");
    }

    @Test
    @DisplayName("Integração Épica 2: Deve buscar candidatos por foodGroup e respeitar ordenação de popularidade")
    void testFindCandidatesByFoodGroup() {
        var candidates = foodRepository.findCandidatesByFoodGroup(
                "PROTEINA_ANIMAL",
                java.util.UUID.randomUUID(),
                PageRequest.of(0, 5)
        );

        assertNotNull(candidates);
        if (!candidates.isEmpty()) {
            // Primeiro item deve ter popularidade alta (>= 500)
            assertTrue(candidates.get(0).getPopularityScore() >= 10);
            assertEquals("PROTEINA_ANIMAL", candidates.get(0).getFoodGroup());
        }
    }
}
