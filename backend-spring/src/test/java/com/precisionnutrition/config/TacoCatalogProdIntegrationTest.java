package com.precisionnutrition.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.precisionnutrition.model.dto.TargetCalculationRequest;
import com.precisionnutrition.model.entity.Meal;
import com.precisionnutrition.model.enums.ActivityLevel;
import com.precisionnutrition.model.enums.DietGoal;
import com.precisionnutrition.model.enums.FoodSource;
import com.precisionnutrition.model.enums.Gender;
import com.precisionnutrition.model.enums.UserRole;
import com.precisionnutrition.repository.DailyTargetRepository;
import com.precisionnutrition.repository.FoodRepository;
import com.precisionnutrition.repository.MealRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.precisionnutrition.support.TestAuthSupport.asDefaultUser;
import static com.precisionnutrition.support.TestAuthSupport.asUser;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "app.security.jwt.secret=test-prod-profile-jwt-secret-key-at-least-32-bytes-long"
})
class TacoCatalogProdIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FoodRepository foodRepository;

    @Autowired
    private TacoCatalogSeeder tacoCatalogSeeder;

    @Autowired
    private MealRepository mealRepository;

    @Autowired
    private DailyTargetRepository dailyTargetRepository;

    @Test
    @DisplayName("Produção (profile=prod): Catálogo TACO deve subir automaticamente, priorizar busca local e ser 100% idempotente")
    void testTacoCatalogLoadsInProdAndIsIdempotent() throws Exception {
        long tacoCount = foodRepository.countBySource(FoodSource.TACO);
        assertTrue(tacoCount >= 500, "Catálogo TACO completo deve estar carregado no perfil prod (atual: " + tacoCount + ")");

        // Segunda execução deve ser 100% idempotente (0 novas inserções)
        int newlyInserted = tacoCatalogSeeder.seedTacoCatalog();
        assertEquals(0, newlyInserted, "Reexecução do TacoCatalogSeeder não deve duplicar registros");

        // Busca por 'goma' deve retornar Tapioca TACO localmente com prioridade
        mockMvc.perform(get("/api/v1/foods/search")
                        .with(asDefaultUser())
                        .param("q", "goma")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", not(empty())))
                .andExpect(jsonPath("$.content[0].source", is("TACO")))
                .andExpect(jsonPath("$.content[0].name", containsStringIgnoringCase("Tapioca")));
    }

    @Test
    @DisplayName("Produção (profile=prod): Ativar protocolo no Wizard gera 4 refeições base se usuário tiver 0 refeições e não duplica")
    void testWizardGeneratesBaseMealsOnceWhenEmpty() throws Exception {
        UUID freshUserId = UUID.randomUUID();
        assertTrue(mealRepository.findByUserIdOrderByOrderIndexAsc(freshUserId).isEmpty());

        try {
            TargetCalculationRequest request = new TargetCalculationRequest(
                    freshUserId,
                    Gender.MALE,
                    25,
                    new BigDecimal("72.00"),
                    new BigDecimal("176.00"),
                    ActivityLevel.MODERATE,
                    DietGoal.LOSE_FAT,
                    new BigDecimal("2.00"),
                    true
            );

            // 1. Primeira ativação com 0 refeições -> deve gerar as 4 refeições base com itens TACO
            mockMvc.perform(post("/api/v1/targets/calculate-and-apply")
                            .with(asUser(freshUserId, UserRole.USER))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.isActive", is(true)));

            List<Meal> generatedMeals = mealRepository.findByUserIdOrderByOrderIndexAsc(freshUserId);
            assertEquals(4, generatedMeals.size(), "Wizard deve gerar 4 refeições base (Café, Almoço, Lanche, Jantar)");
            assertEquals("Café da Manhã", generatedMeals.get(0).getName());
            assertEquals("Almoço", generatedMeals.get(1).getName());
            assertEquals("Lanche da Tarde", generatedMeals.get(2).getName());
            assertEquals("Jantar", generatedMeals.get(3).getName());

            // 2. Segunda ativação quando o usuário JÁ possui refeições -> NÃO pode duplicar nem sobrescrever
            mockMvc.perform(post("/api/v1/targets/calculate-and-apply")
                            .with(asUser(freshUserId, UserRole.USER))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            List<Meal> mealsAfterSecondApply = mealRepository.findByUserIdOrderByOrderIndexAsc(freshUserId);
            assertEquals(4, mealsAfterSecondApply.size(), "Refeições existentes não podem ser duplicadas");
        } finally {
            mealRepository.deleteAll(mealRepository.findByUserIdOrderByOrderIndexAsc(freshUserId));
            dailyTargetRepository.deleteAll(dailyTargetRepository.findByUserIdOrderByCreatedAtDesc(freshUserId));
        }
    }
}
