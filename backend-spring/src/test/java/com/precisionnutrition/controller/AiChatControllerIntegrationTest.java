package com.precisionnutrition.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.precisionnutrition.client.GeminiClient;
import com.precisionnutrition.model.dto.AiChatMessageDto;
import com.precisionnutrition.model.dto.AiChatRequest;
import com.precisionnutrition.model.enums.UserRole;
import com.precisionnutrition.service.AiRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.precisionnutrition.support.TestAuthSupport.asDefaultUser;
import static com.precisionnutrition.support.TestAuthSupport.asUser;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AiChatControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AiRateLimiter aiRateLimiter;

    @MockBean
    private GeminiClient geminiClient;

    @BeforeEach
    void setUp() {
        aiRateLimiter.resetAll();
        reset(geminiClient);
        when(geminiClient.isConfigured()).thenReturn(true);
        when(geminiClient.getModel()).thenReturn("gemini-2.5-flash");
        when(geminiClient.generateContent(anyString(), anyList()))
                .thenReturn("Sugestão: **150g de peito de frango grelhado** com **120g de arroz cozido** para completar sua meta de proteína.");
    }

    @Test
    @DisplayName("IA 1: POST /api/v1/ai/chat sem token JWT deve retornar HTTP 401")
    void testChatWithoutTokenReturns401() throws Exception {
        AiChatRequest request = new AiChatRequest("Como está meu dia?", LocalDate.now(), null);

        mockMvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));

        verify(geminiClient, never()).generateContent(anyString(), anyList());
    }

    @Test
    @DisplayName("IA 2: Mensagem vazia ou acima de 1000 caracteres deve retornar HTTP 400")
    void testChatValidationErrorsReturn400() throws Exception {
        AiChatRequest blankRequest = new AiChatRequest("   ", LocalDate.now(), null);

        mockMvc.perform(post("/api/v1/ai/chat")
                        .with(asDefaultUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));

        AiChatRequest oversizedRequest = new AiChatRequest("a".repeat(1001), LocalDate.now(), null);

        mockMvc.perform(post("/api/v1/ai/chat")
                        .with(asDefaultUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(oversizedRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));

        verify(geminiClient, never()).generateContent(anyString(), anyList());
    }

    @Test
    @DisplayName("IA 3: Quando GEMINI_API_KEY estiver ausente deve retornar HTTP 503 com mensagem amigável")
    void testChatReturns503WhenGeminiNotConfigured() throws Exception {
        when(geminiClient.isConfigured()).thenReturn(false);

        AiChatRequest request = new AiChatRequest("O que comer agora?", LocalDate.now(), null);

        mockMvc.perform(post("/api/v1/ai/chat")
                        .with(asDefaultUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status", is(503)))
                .andExpect(jsonPath("$.message", containsStringIgnoringCase("GEMINI_API_KEY")));
    }

    @Test
    @DisplayName("IA 4: Deve injetar contexto real do dia no System Instruction e retornar HTTP 200 com resposta do Gemini")
    void testChatSuccessWithContextInjectionReturns200() throws Exception {
        LocalDate today = LocalDate.now();
        AiChatRequest request = new AiChatRequest(
                "O que comer agora para bater meus macros?",
                today,
                List.of(
                        new AiChatMessageDto("user", "Oi!"),
                        new AiChatMessageDto("model", "Olá! Como posso ajudar na sua dieta hoje?")
                )
        );

        mockMvc.perform(post("/api/v1/ai/chat")
                        .with(asDefaultUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply", containsString("peito de frango")))
                .andExpect(jsonPath("$.model", is("gemini-2.5-flash")));

        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AiChatMessageDto>> turnsCaptor = ArgumentCaptor.forClass(List.class);
        verify(geminiClient, times(1)).generateContent(systemPromptCaptor.capture(), turnsCaptor.capture());

        String capturedSystemPrompt = systemPromptCaptor.getValue();
        assertTrue(capturedSystemPrompt.contains("CONTEXTO REAL DO USUÁRIO"), "Deve injetar o contexto real do usuário");
        assertTrue(capturedSystemPrompt.contains(today.toString()), "Deve incluir a data consultada no contexto");
        assertTrue(capturedSystemPrompt.contains("Calorias:"), "Deve incluir resumo de calorias");
        assertTrue(capturedSystemPrompt.contains("Proteínas:"), "Deve incluir saldo de proteínas");
        assertTrue(capturedSystemPrompt.contains("REFEIÇÕES DO PLANO NO DIA"), "Deve incluir resumo das refeições");

        List<AiChatMessageDto> sentTurns = turnsCaptor.getValue();
        assertTrue(sentTurns.size() == 3, "Deve enviar o histórico + a mensagem atual do usuário");
    }

    @Test
    @DisplayName("IA 5: Rate Limit por usuário deve bloquear a 11ª requisição no mesmo minuto com HTTP 429")
    void testRateLimitTriggers429AfterTenRequestsPerMinute() throws Exception {
        UUID isolatedUserId = UUID.randomUUID();
        AiChatRequest request = new AiChatRequest("Como está meu dia até agora?", LocalDate.now(), null);
        String body = objectMapper.writeValueAsString(request);

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/v1/ai/chat")
                            .with(asUser(isolatedUserId, UserRole.USER))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }

        // 11ª requisição do mesmo usuário no minuto -> HTTP 429 Too Many Requests
        mockMvc.perform(post("/api/v1/ai/chat")
                        .with(asUser(isolatedUserId, UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status", is(429)))
                .andExpect(jsonPath("$.message", containsStringIgnoringCase("limite")));
    }
}
