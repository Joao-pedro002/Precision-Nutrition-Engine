package com.precisionnutrition.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.precisionnutrition.model.dto.CheckMealRequest;
import com.precisionnutrition.model.dto.OffDietEntryRequest;
import com.precisionnutrition.model.dto.WaterUpdateRequest;
import com.precisionnutrition.model.entity.Meal;
import com.precisionnutrition.repository.DailyLogRepository;
import com.precisionnutrition.repository.MealRepository;
import com.precisionnutrition.util.AppConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DashboardControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MealRepository mealRepository;

    @Autowired
    private DailyLogRepository dailyLogRepository;

    @BeforeEach
    void setUp() {
        dailyLogRepository.deleteAll();
    }

    @Test
    @DisplayName("Integração: Deve carregar o dashboard completo com meta ativa, refeições prescritas e matriz de micronutrientes")
    void testGetDashboard() throws Exception {
        LocalDate today = LocalDate.now();

        mockMvc.perform(get("/api/v1/dashboard")
                        .param("date", today.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date", is(today.toString())))
                .andExpect(jsonPath("$.activeTarget", notNullValue()))
                .andExpect(jsonPath("$.activeTarget.targetProteinG", is(180.0)))
                .andExpect(jsonPath("$.summary", notNullValue()))
                .andExpect(jsonPath("$.micronutrients", notNullValue()))
                .andExpect(jsonPath("$.micronutrients.comparisons", hasSize(9)))
                .andExpect(jsonPath("$.meals", not(empty())));
    }

    @Test
    @DisplayName("Integração: Deve atualizar hidratação incrementalmente e refletir na porcentagem da meta")
    void testUpdateWater() throws Exception {
        LocalDate testDate = LocalDate.now().plusDays(10); // Data isolada para o teste

        WaterUpdateRequest request = new WaterUpdateRequest("INCREMENT", new BigDecimal("500.00"), AppConstants.DEFAULT_USER_ID);

        mockMvc.perform(patch("/api/v1/logs/" + testDate + "/water")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date", is(testDate.toString())))
                .andExpect(jsonPath("$.waterConsumedMl", is(500.0)))
                .andExpect(jsonPath("$.waterTargetMl", is(3500.0)))
                .andExpect(jsonPath("$.waterPercent", is(14.29)));
    }

    @Test
    @DisplayName("Integração: Deve checar uma opção de refeição (toggle on) e desmarcar (toggle off)")
    void testCheckAndUncheckMealOption() throws Exception {
        LocalDate testDate = LocalDate.now().plusDays(20);

        List<Meal> meals = mealRepository.findByUserIdOrderByOrderIndexAsc(AppConstants.DEFAULT_USER_ID);
        assertFalse(meals.isEmpty(), "Deve haver refeições cadastradas pelo Seeder");
        Meal firstMeal = meals.get(0);

        CheckMealRequest checkRequest = new CheckMealRequest(firstMeal.getId(), 1, AppConstants.DEFAULT_USER_ID);

        // 1. Toggle ON
        mockMvc.perform(post("/api/v1/logs/" + testDate + "/check-meal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", not(empty())));

        // 2. Verificar no Dashboard que a refeição está completada
        mockMvc.perform(get("/api/v1/dashboard")
                        .param("date", testDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meals[0].isCompleted", is(true)))
                .andExpect(jsonPath("$.meals[0].completedOptionNumber", is(1)))
                .andExpect(jsonPath("$.summary.consumedProteinG", greaterThan(0.0)));

        // 3. Toggle OFF (Clicar novamente na mesma opção)
        mockMvc.perform(post("/api/v1/logs/" + testDate + "/check-meal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", empty()));
    }

    @Test
    @DisplayName("Integração: Deve registrar entrada avulsa fora da dieta (isOffDiet = true)")
    void testAddOffDietEntry() throws Exception {
        LocalDate testDate = LocalDate.now().plusDays(30);

        OffDietEntryRequest request = new OffDietEntryRequest(
                null,
                "Fatia de Pizza Margherita",
                new BigDecimal("120.00"),
                new BigDecimal("320.00"),
                new BigDecimal("12.00"),
                new BigDecimal("35.00"),
                new BigDecimal("15.00"),
                new BigDecimal("2.00"),
                AppConstants.DEFAULT_USER_ID
        );

        mockMvc.perform(post("/api/v1/logs/" + testDate + "/off-diet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.foodNameSnapshot", is("Fatia de Pizza Margherita")))
                .andExpect(jsonPath("$.isOffDiet", is(true)))
                .andExpect(jsonPath("$.consumedEnergyKcal", is(320.0)));
    }
}
