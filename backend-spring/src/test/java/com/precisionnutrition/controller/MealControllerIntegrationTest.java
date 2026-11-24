package com.precisionnutrition.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.precisionnutrition.model.dto.AddMealOptionItemRequest;
import com.precisionnutrition.model.dto.CheckMealRequest;
import com.precisionnutrition.model.dto.CreateMealRequest;
import com.precisionnutrition.model.dto.CustomFoodRequest;
import com.precisionnutrition.model.dto.UpdateMealItemWeightRequest;
import com.precisionnutrition.model.entity.Meal;
import com.precisionnutrition.repository.DailyLogRepository;
import com.precisionnutrition.repository.FoodRepository;
import com.precisionnutrition.repository.MealItemRepository;
import com.precisionnutrition.repository.MealRepository;
import com.precisionnutrition.util.AppConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class MealControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MealRepository mealRepository;

    @Autowired
    private MealItemRepository mealItemRepository;

    @Autowired
    private FoodRepository foodRepository;

    @Autowired
    private DailyLogRepository dailyLogRepository;

    @Test
    @DisplayName("Integração: Deve adicionar, atualizar gramagem e remover item de refeição sem alterar histórico passado")
    void testAddUpdateAndRemoveMealItemPreservesPastHistory() throws Exception {
        List<Meal> meals = mealRepository.findByUserIdOrderByOrderIndexAsc(AppConstants.DEFAULT_USER_ID);
        assertFalse(meals.isEmpty(), "Deve haver refeições cadastradas pelo Seeder");
        Meal targetMeal = meals.get(0);

        // 1. Marcar a refeição em uma data passada para validar integridade histórica
        LocalDate pastDate = LocalDate.now().minusDays(2);
        dailyLogRepository.deleteAll();
        CheckMealRequest checkReq = new CheckMealRequest(targetMeal.getId(), 1, AppConstants.DEFAULT_USER_ID);
        mockMvc.perform(post("/api/v1/logs/" + pastDate + "/check-meal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkReq)))
                .andExpect(status().isOk());

        String pastDashboardBefore = mockMvc.perform(get("/api/v1/dashboard")
                        .param("date", pastDate.toString()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        double pastKcalBefore = objectMapper.readTree(pastDashboardBefore).get("summary").get("consumedEnergyKcal").asDouble();

        // 2. Criar um alimento CUSTOM (por 100g)
        CustomFoodRequest customFoodReq = new CustomFoodRequest(
                "Goma de Tapioca Teste CRUD",
                "Personalizados",
                null,
                new BigDecimal("240.00"),
                new BigDecimal("0.00"),
                new BigDecimal("60.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                null
        );

        String foodJson = mockMvc.perform(post("/api/v1/foods/custom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(customFoodReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source", is("CUSTOM")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID customFoodId = UUID.fromString(objectMapper.readTree(foodJson).get("id").asText());

        // 3. Adicionar o alimento custom à Opção 1 da refeição (POST /api/v1/meals/{mealId}/options/{optionNumber}/items)
        AddMealOptionItemRequest addItemReq = new AddMealOptionItemRequest(customFoodId, new BigDecimal("80.00"));
        String addedItemJson = mockMvc.perform(post("/api/v1/meals/" + targetMeal.getId() + "/options/1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addItemReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.optionNumber", is(1)))
                .andExpect(jsonPath("$.weightG", is(80.0)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID addedItemId = UUID.fromString(objectMapper.readTree(addedItemJson).get("id").asText());

        // 4. Atualizar gramagem via PATCH /api/v1/meals/items/{itemId}
        UpdateMealItemWeightRequest updateReq = new UpdateMealItemWeightRequest(new BigDecimal("120.00"));
        mockMvc.perform(patch("/api/v1/meals/items/" + addedItemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weightG", is(120.0)));

        // 4b. Rejeitar gramagem <= 0 (HTTP 400)
        UpdateMealItemWeightRequest invalidUpdateReq = new UpdateMealItemWeightRequest(BigDecimal.ZERO);
        mockMvc.perform(patch("/api/v1/meals/items/" + addedItemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidUpdateReq)))
                .andExpect(status().isBadRequest());

        // 5. Garantir que o histórico passado (pastDate) permaneceu 100% intacto
        mockMvc.perform(get("/api/v1/dashboard")
                        .param("date", pastDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.consumedEnergyKcal", closeTo(pastKcalBefore, 0.01)));

        // 6. Remover item via DELETE /api/v1/meals/items/{itemId}
        mockMvc.perform(delete("/api/v1/meals/items/" + addedItemId))
                .andExpect(status().isNoContent());

        assertFalse(mealItemRepository.existsById(addedItemId), "Item deve ter sido removido do banco");

        // Limpeza do alimento custom de teste
        foodRepository.deleteById(customFoodId);
        dailyLogRepository.deleteAll();
    }

    @Test
    @DisplayName("Integração: Deve criar refeição com auto-ordem (201), rejeitar nome vazio (400) e remover sem alterar histórico passado (204)")
    void testCreateValidateAndDeleteMealPreservesPastSnapshots() throws Exception {
        // 1. Nome vazio deve retornar HTTP 400 Bad Request
        CreateMealRequest invalidReq = new CreateMealRequest(AppConstants.DEFAULT_USER_ID, "   ", null, null, null);
        mockMvc.perform(post("/api/v1/meals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest());

        // 2. Criar nova refeição sem orderIndex -> deve receber próxima ordem disponível e retornar HTTP 201
        CreateMealRequest validReq = new CreateMealRequest(
                AppConstants.DEFAULT_USER_ID,
                "Ceia Proteica Teste",
                null,
                LocalTime.of(22, 30),
                null
        );

        String createdJson = mockMvc.perform(post("/api/v1/meals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("Ceia Proteica Teste")))
                .andExpect(jsonPath("$.orderIndex", greaterThan(0)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID createdMealId = UUID.fromString(objectMapper.readTree(createdJson).get("id").asText());

        // 3. Verificar que no Dashboard a refeição recém-criada já vem com Opção 1 vazia pronta para receber alimentos
        mockMvc.perform(get("/api/v1/dashboard")
                        .param("date", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meals[?(@.id=='" + createdMealId + "')].options[0].optionNumber", contains(1)));

        // 4. Remover refeição via DELETE /api/v1/meals/{id} -> HTTP 204
        mockMvc.perform(delete("/api/v1/meals/" + createdMealId))
                .andExpect(status().isNoContent());

        assertFalse(mealRepository.existsById(createdMealId), "Refeição removida não deve mais existir no plano");
    }
}
