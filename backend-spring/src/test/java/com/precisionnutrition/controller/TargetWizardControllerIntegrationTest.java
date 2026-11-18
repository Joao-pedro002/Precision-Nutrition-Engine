package com.precisionnutrition.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.precisionnutrition.model.dto.TargetCalculationRequest;
import com.precisionnutrition.model.enums.ActivityLevel;
import com.precisionnutrition.model.enums.DietGoal;
import com.precisionnutrition.model.enums.Gender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TargetWizardControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Integração Épica 1: Deve calcular BMR e TDEE via POST /api/v1/targets/calculate")
    void testCalculateTargets() throws Exception {
        TargetCalculationRequest request = new TargetCalculationRequest(
                UUID.randomUUID(),
                Gender.MALE,
                20,
                new BigDecimal("68.00"),
                new BigDecimal("173.00"),
                ActivityLevel.MODERATE,
                DietGoal.LOSE_FAT
        );

        mockMvc.perform(post("/api/v1/targets/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bmrKcal", is(1666.25)))
                .andExpect(jsonPath("$.tdeeKcal", is(2582.69)))
                .andExpect(jsonPath("$.targetKcalMax", is(2066.15)))
                .andExpect(jsonPath("$.targetProteinG", is(149.60)))
                .andExpect(jsonPath("$.targetLipidG", is(54.40)))
                .andExpect(jsonPath("$.targetSource", is("CALCULATED")));
    }

    @Test
    @DisplayName("Integração Épica 1: Deve calcular e aplicar novo protocolo via POST /api/v1/targets/calculate-and-apply")
    void testCalculateAndApply() throws Exception {
        TargetCalculationRequest request = new TargetCalculationRequest(
                UUID.randomUUID(),
                Gender.FEMALE,
                28,
                new BigDecimal("58.00"),
                new BigDecimal("162.00"),
                ActivityLevel.LIGHT,
                DietGoal.MAINTAIN
        );

        mockMvc.perform(post("/api/v1/targets/calculate-and-apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.isActive", is(true)))
                .andExpect(jsonPath("$.targetSource", is("CALCULATED")))
                .andExpect(jsonPath("$.targetKcalMax", greaterThan(0.0)));
    }

    @Test
    @DisplayName("Integração: Deve desativar meta ativa via POST /api/v1/targets/deactivate")
    void testDeactivateTargetEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/targets/deactivate"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Integração Épica 3: Deve executar expurgo manual via POST /api/v1/logs/purge-old")
    void testPurgeOldLogsEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/logs/purge-old")
                        .param("days", "6")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purgedCount", notNullValue()))
                .andExpect(jsonPath("$.cutoffDate", notNullValue()))
                .andExpect(jsonPath("$.message", containsString("TTL de 6 dias")));
    }
}
