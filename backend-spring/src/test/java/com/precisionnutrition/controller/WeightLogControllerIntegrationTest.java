package com.precisionnutrition.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.precisionnutrition.model.dto.WeightLogRequest;
import com.precisionnutrition.model.entity.WeightLog;
import com.precisionnutrition.repository.WeightLogRepository;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WeightLogControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WeightLogRepository weightLogRepository;

    @BeforeEach
    void setUp() {
        weightLogRepository.deleteAll();
    }

    @Test
    @DisplayName("Integração: Deve registrar peso com idempotência por data e calcular variação no histórico")
    void testRecordWeightIdempotencyAndHistoryDelta() throws Exception {
        LocalDate day1 = LocalDate.now().minusDays(3);
        LocalDate day2 = LocalDate.now();

        // 1. Primeira pesagem
        WeightLogRequest req1 = new WeightLogRequest(day1, new BigDecimal("68.50"), AppConstants.DEFAULT_USER_ID);
        mockMvc.perform(post("/api/v1/weight")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weightKg", is(68.5)))
                .andExpect(jsonPath("$.logDate", is(day1.toString())));

        // 2. Segunda pesagem em data posterior (hoje)
        WeightLogRequest req2 = new WeightLogRequest(day2, new BigDecimal("68.30"), AppConstants.DEFAULT_USER_ID);
        mockMvc.perform(post("/api/v1/weight")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weightKg", is(68.3)));

        // 3. Atualização idempotente na mesma data (day2: 68.30 -> 68.20)
        WeightLogRequest req2Update = new WeightLogRequest(day2, new BigDecimal("68.20"), AppConstants.DEFAULT_USER_ID);
        mockMvc.perform(post("/api/v1/weight")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2Update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weightKg", is(68.2)));

        // 4. Histórico ordenado decrescentemente com delta exato (-0.30 kg) e apenas 2 registros
        mockMvc.perform(get("/api/v1/weight/history")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestWeightKg", is(68.2)))
                .andExpect(jsonPath("$.latestLogDate", is(day2.toString())))
                .andExpect(jsonPath("$.previousWeightKg", is(68.5)))
                .andExpect(jsonPath("$.deltaKg", is(-0.3)))
                .andExpect(jsonPath("$.history", hasSize(2)));
    }

    @Test
    @DisplayName("Integração: Deve rejeitar registro de peso em data futura com HTTP 400 e ignorar registros futuros residuais no histórico")
    void testRejectFutureDateAndFilterResidualFutureLogs() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);

        // 1. Tentar registrar peso em data futura deve retornar HTTP 400 Bad Request
        WeightLogRequest futureReq = new WeightLogRequest(tomorrow, new BigDecimal("67.50"), AppConstants.DEFAULT_USER_ID);
        mockMvc.perform(post("/api/v1/weight")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(futureReq)))
                .andExpect(status().isBadRequest());

        // 2. Registrar peso válido hoje e simular um registro futuro residual salvo direto no banco
        weightLogRepository.save(WeightLog.builder()
                .userId(AppConstants.DEFAULT_USER_ID)
                .logDate(today)
                .weightKg(new BigDecimal("68.00"))
                .build());

        weightLogRepository.save(WeightLog.builder()
                .userId(AppConstants.DEFAULT_USER_ID)
                .logDate(today.plusDays(5))
                .weightKg(new BigDecimal("65.00"))
                .build());

        // 3. GET /history deve ignorar o registro futuro residual e manter 68.00 kg de hoje como última pesagem
        mockMvc.perform(get("/api/v1/weight/history")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestWeightKg", is(68.0)))
                .andExpect(jsonPath("$.latestLogDate", is(today.toString())))
                .andExpect(jsonPath("$.history", hasSize(1)));
    }
}
