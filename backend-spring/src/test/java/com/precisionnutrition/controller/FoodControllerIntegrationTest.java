package com.precisionnutrition.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.precisionnutrition.model.dto.FoodCreateRequest;
import com.precisionnutrition.model.dto.MicronutrientsData;
import com.precisionnutrition.model.dto.SubstitutionRequest;
import com.precisionnutrition.model.entity.Food;
import com.precisionnutrition.repository.FoodRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FoodControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FoodRepository foodRepository;

    @Test
    @DisplayName("Integração: Deve buscar alimentos na base local via /api/v1/foods/search")
    void testSearchFoods() throws Exception {
        mockMvc.perform(get("/api/v1/foods/search")
                        .param("q", "frango")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", not(empty())))
                .andExpect(jsonPath("$.content[0].name", containsStringIgnoringCase("frango")));
    }

    @Test
    @DisplayName("Integração: Deve cadastrar um alimento customizado via /api/v1/foods/custom")
    void testCreateCustomFood() throws Exception {
        FoodCreateRequest request = new FoodCreateRequest(
                "Whey Protein Isolado 90%",
                "Suplementos",
                null, // Deixa classificar automaticamente como PROTEIN
                new BigDecimal("110.00"),
                new BigDecimal("27.00"),
                new BigDecimal("0.50"),
                new BigDecimal("0.20"),
                BigDecimal.ZERO,
                MicronutrientsData.empty()
        );

        mockMvc.perform(post("/api/v1/foods/custom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("Whey Protein Isolado 90%")))
                .andExpect(jsonPath("$.source", is("CUSTOM")))
                .andExpect(jsonPath("$.dominantMacro", is("PROTEIN")));
    }

    @Test
    @DisplayName("Integração: Deve calcular substituição equivalente entre dois alimentos via /api/v1/nutrition/substitute")
    void testSubstituteFood() throws Exception {
        List<Food> frangos = foodRepository.searchByName("Frango, peito, sem pele, grelhado", PageRequest.of(0, 1)).getContent();
        List<Food> ovos = foodRepository.searchByName("Ovo, de galinha, inteiro, cozido", PageRequest.of(0, 1)).getContent();

        assertFalse(frangos.isEmpty(), "Frango deve existir na base TACO");
        assertFalse(ovos.isEmpty(), "Ovo deve existir na base TACO");

        Food frango = frangos.get(0);
        Food ovo = ovos.get(0);

        SubstitutionRequest request = new SubstitutionRequest(
                frango.getId(),
                new BigDecimal("150.00"),
                ovo.getId(),
                null // Usa regra híbrida automática (macro dominante do frango = PROTEIN)
        );

        mockMvc.perform(post("/api/v1/nutrition/substitute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceFood.name", containsString("Frango")))
                .andExpect(jsonPath("$.targetFood.name", containsString("Ovo")))
                .andExpect(jsonPath("$.targetFood.weightG", greaterThan(0.0)))
                .andExpect(jsonPath("$.calorieDriftWarning", is(true)))
                .andExpect(jsonPath("$.warningMessage", notNullValue()));
    }

    @Test
    @DisplayName("Integração Épica 2: Deve retornar Top 5 Quick Substitutions com menor Calorie Drift via REST")
    void testQuickSubstitutionsEndpoint() throws Exception {
        List<Food> frangos = foodRepository.searchByName("Frango, peito, sem pele, grelhado", PageRequest.of(0, 1)).getContent();
        assertFalse(frangos.isEmpty(), "Frango deve existir na base");
        Food frango = frangos.get(0);

        mockMvc.perform(get("/api/v1/nutrition/quick-substitutions")
                        .param("foodId", frango.getId().toString())
                        .param("weightG", "150.00")
                        .param("limit", "5")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())))
                .andExpect(jsonPath("$[0].targetFoodName", notNullValue()))
                .andExpect(jsonPath("$[0].calculatedWeightG", greaterThan(0.0)))
                .andExpect(jsonPath("$[0].deltaKcal", notNullValue()));
    }
}
