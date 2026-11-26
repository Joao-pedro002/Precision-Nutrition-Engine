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

import static com.precisionnutrition.support.TestAuthSupport.asDefaultUser;
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
                        .with(asDefaultUser())
                        .param("date", today.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date", is(today.toString())))
                .andExpect(jsonPath("$.activeTarget", notNullValue()))
                .andExpect(jsonPath("$.activeTarget.targetProteinG", greaterThan(0.0)))
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
                        .with(asDefaultUser())
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
                        .with(asDefaultUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", not(empty())));

        // 2. Verificar no Dashboard que a refeição está completada
        mockMvc.perform(get("/api/v1/dashboard")
                        .with(asDefaultUser())
                        .param("date", testDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meals[0].isCompleted", is(true)))
                .andExpect(jsonPath("$.meals[0].completedOptionNumber", is(1)))
                .andExpect(jsonPath("$.summary.consumedProteinG", greaterThan(0.0)));

        // 3. Toggle OFF (Clicar novamente na mesma opção)
        mockMvc.perform(post("/api/v1/logs/" + testDate + "/check-meal")
                        .with(asDefaultUser())
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
                        .with(asDefaultUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.foodNameSnapshot", is("Fatia de Pizza Margherita")))
                .andExpect(jsonPath("$.isOffDiet", is(true)))
                .andExpect(jsonPath("$.consumedEnergyKcal", is(320.0)));
    }

    @Test
    @DisplayName("Integração: Deve desmarcar refeição via endpoint dedicado uncheck-meal")
    void testUncheckMealOptionEndpoint() throws Exception {
        LocalDate testDate = LocalDate.now().plusDays(40);

        List<Meal> meals = mealRepository.findByUserIdOrderByOrderIndexAsc(AppConstants.DEFAULT_USER_ID);
        Meal firstMeal = meals.get(0);

        CheckMealRequest checkRequest = new CheckMealRequest(firstMeal.getId(), 1, AppConstants.DEFAULT_USER_ID);

        // 1. Marcar primeiro
        mockMvc.perform(post("/api/v1/logs/" + testDate + "/check-meal")
                        .with(asDefaultUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", not(empty())));

        // 2. Desmarcar via /uncheck-meal
        mockMvc.perform(post("/api/v1/logs/" + testDate + "/uncheck-meal")
                        .with(asDefaultUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", empty()));
    }

    @Test
    @DisplayName("Integração: Refeição livre deve somar calorias no Dashboard e permitir cancelamento")
    void testOffDietEntryCalculatesCaloriesInDashboardAndCanBeUndone() throws Exception {
        LocalDate testDate = LocalDate.now().plusDays(50);

        List<Meal> meals = mealRepository.findByUserIdOrderByOrderIndexAsc(AppConstants.DEFAULT_USER_ID);
        Meal firstMeal = meals.get(0);

        OffDietEntryRequest offDietRequest = new OffDietEntryRequest(
                firstMeal.getId(),
                null,
                "Almoço Livre Especial",
                new BigDecimal("500.00"),
                new BigDecimal("2500.00"),
                new BigDecimal("80.00"),
                new BigDecimal("200.00"),
                new BigDecimal("120.00"),
                new BigDecimal("10.00"),
                AppConstants.DEFAULT_USER_ID
        );

        // 1. Inserir refeição livre vinculada à refeição 1
        mockMvc.perform(post("/api/v1/logs/" + testDate + "/off-diet")
                        .with(asDefaultUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(offDietRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.consumedEnergyKcal", is(2500.0)));

        // 2. Verificar se o Dashboard reflete as 2500 kcal da refeição livre
        mockMvc.perform(get("/api/v1/dashboard")
                        .with(asDefaultUser())
                        .param("date", testDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.consumedEnergyKcal", is(2500.0)))
                .andExpect(jsonPath("$.summary.consumedProteinG", is(80.0)))
                .andExpect(jsonPath("$.loggedEntries", hasSize(1)))
                .andExpect(jsonPath("$.loggedEntries[0].isOffDiet", is(true)))
                .andExpect(jsonPath("$.loggedEntries[0].mealId", is(firstMeal.getId().toString())));

        // 3. Desfazer / cancelar a refeição livre
        CheckMealRequest uncheckRequest = new CheckMealRequest(firstMeal.getId(), 1, AppConstants.DEFAULT_USER_ID);
        mockMvc.perform(post("/api/v1/logs/" + testDate + "/uncheck-meal")
                        .with(asDefaultUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(uncheckRequest)))
                .andExpect(status().isOk());

        // 4. Confirmar que as calorias voltaram para 0
        mockMvc.perform(get("/api/v1/dashboard")
                        .with(asDefaultUser())
                        .param("date", testDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.consumedEnergyKcal", is(0.0)))
                .andExpect(jsonPath("$.loggedEntries", empty()));
    }

    @Test
    @DisplayName("Integração: Deve somar P, C e G no DailySummaryDto ao marcar refeição e blindar macros nulos em refeição livre")
    void testGlobalMacroAggregationAndNullSafeOffDiet() throws Exception {
        LocalDate testDate = LocalDate.now().plusDays(60);

        List<Meal> meals = mealRepository.findByUserIdOrderByOrderIndexAsc(AppConstants.DEFAULT_USER_ID);
        assertFalse(meals.isEmpty(), "Deve haver refeições cadastradas pelo Seeder");
        Meal firstMeal = meals.get(0);

        // 1. Marcar a primeira refeição prescrita (Opção 1)
        CheckMealRequest checkRequest = new CheckMealRequest(firstMeal.getId(), 1, AppConstants.DEFAULT_USER_ID);
        mockMvc.perform(post("/api/v1/logs/" + testDate + "/check-meal")
                        .with(asDefaultUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkRequest)))
                .andExpect(status().isOk());

        // 2. Validar soma de P, C, G e percentuais no Dashboard
        String dashboardJsonAfterMeal = mockMvc.perform(get("/api/v1/dashboard")
                        .with(asDefaultUser())
                        .param("date", testDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.consumedProteinG", greaterThan(0.0)))
                .andExpect(jsonPath("$.summary.consumedCarbohydrateG", greaterThan(0.0)))
                .andExpect(jsonPath("$.summary.consumedLipidG", greaterThan(0.0)))
                .andExpect(jsonPath("$.summary.targetProteinG", greaterThan(0.0)))
                .andExpect(jsonPath("$.summary.targetCarbohydrateG", greaterThan(0.0)))
                .andExpect(jsonPath("$.summary.targetLipidG", greaterThan(0.0)))
                .andExpect(jsonPath("$.summary.proteinPercentage", greaterThan(0.0)))
                .andExpect(jsonPath("$.summary.carbohydratePercentage", greaterThan(0.0)))
                .andExpect(jsonPath("$.summary.lipidPercentage", greaterThan(0.0)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        var summaryBeforeOffDiet = objectMapper.readTree(dashboardJsonAfterMeal).get("summary");
        double kcalBefore = summaryBeforeOffDiet.get("consumedEnergyKcal").asDouble();
        double proteinBefore = summaryBeforeOffDiet.get("consumedProteinG").asDouble();
        double carbsBefore = summaryBeforeOffDiet.get("consumedCarbohydrateG").asDouble();
        double fatBefore = summaryBeforeOffDiet.get("consumedLipidG").asDouble();

        // 3. Lançar refeição livre informando APENAS calorias (macros nulos)
        OffDietEntryRequest offDietOnlyCalories = new OffDietEntryRequest(
                null,
                null,
                "Sorvete Artesanal (Só Calorias)",
                new BigDecimal("150.00"),
                new BigDecimal("450.00"),
                null,
                null,
                null,
                null,
                AppConstants.DEFAULT_USER_ID
        );

        mockMvc.perform(post("/api/v1/logs/" + testDate + "/off-diet")
                        .with(asDefaultUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(offDietOnlyCalories)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isOffDiet", is(true)))
                .andExpect(jsonPath("$.consumedEnergyKcal", is(450.0)))
                .andExpect(jsonPath("$.consumedProteinG", is(0.0)))
                .andExpect(jsonPath("$.consumedCarbohydrateG", is(0.0)))
                .andExpect(jsonPath("$.consumedLipidG", is(0.0)));

        // 4. Validar que as calorias somaram +450 e os macros P, C e G permaneceram intactos sem erro
        mockMvc.perform(get("/api/v1/dashboard")
                        .with(asDefaultUser())
                        .param("date", testDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.consumedEnergyKcal", closeTo(kcalBefore + 450.0, 0.01)))
                .andExpect(jsonPath("$.summary.consumedProteinG", closeTo(proteinBefore, 0.01)))
                .andExpect(jsonPath("$.summary.consumedCarbohydrateG", closeTo(carbsBefore, 0.01)))
                .andExpect(jsonPath("$.summary.consumedLipidG", closeTo(fatBefore, 0.01)));
    }

    @Test
    @DisplayName("Integração: Data futura inédita ou passada sem registros deve retornar HTTP 200 com resumo zerado e plano ativo")
    void testUnseenFutureAndPastDateReturnsZeroedDashboard() throws Exception {
        LocalDate futureDate = LocalDate.now().plusYears(1);
        LocalDate pastDate = LocalDate.now().minusYears(1);

        for (LocalDate targetDate : List.of(futureDate, pastDate)) {
            mockMvc.perform(get("/api/v1/dashboard")
                            .with(asDefaultUser())
                            .param("date", targetDate.toString())
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.date", is(targetDate.toString())))
                    .andExpect(jsonPath("$.activeTarget", notNullValue()))
                    .andExpect(jsonPath("$.summary.consumedEnergyKcal", is(0.0)))
                    .andExpect(jsonPath("$.summary.consumedProteinG", is(0.0)))
                    .andExpect(jsonPath("$.summary.consumedCarbohydrateG", is(0.0)))
                    .andExpect(jsonPath("$.summary.consumedLipidG", is(0.0)))
                    .andExpect(jsonPath("$.summary.consumedFiberG", is(0.0)))
                    .andExpect(jsonPath("$.summary.waterConsumedMl", is(0.0)))
                    .andExpect(jsonPath("$.loggedEntries", empty()))
                    .andExpect(jsonPath("$.meals", not(empty())));
        }
    }
}
