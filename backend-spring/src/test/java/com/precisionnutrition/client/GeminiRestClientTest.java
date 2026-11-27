package com.precisionnutrition.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeminiRestClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Deve concatenar todos os blocos de texto não-thought de candidates[0].content.parts")
    void testConcatenatesAllNonThoughtTextParts() throws Exception {
        GeminiRestClient client = new GeminiRestClient(
                "test-key",
                "gemini-2.5-flash",
                "https://generativelanguage.googleapis.com/v1beta",
                0.4,
                2048
        );

        String json = """
                {
                  "candidates": [
                    {
                      "finishReason": "STOP",
                      "content": {
                        "role": "model",
                        "parts": [
                          { "thought": true, "text": "Raciocínio interno oculto..." },
                          { "text": "Resumo do seu dia:\\n" },
                          { "text": "- **Proteínas:** faltam 40g\\n" },
                          { "text": "- **Sugestão:** 150g de frango grelhado" }
                        ]
                      }
                    }
                  ]
                }
                """;

        JsonNode root = objectMapper.readTree(json);
        String reply = client.extractReplyText(root);

        assertEquals(
                "Resumo do seu dia:\n- **Proteínas:** faltam 40g\n- **Sugestão:** 150g de frango grelhado",
                reply
        );
    }

    @Test
    @DisplayName("Deve extrair texto integralmente mesmo quando finishReason for MAX_TOKENS")
    void testExtractsTextWhenFinishReasonIsMaxTokens() throws Exception {
        GeminiRestClient client = new GeminiRestClient(
                "test-key",
                "gemini-2.5-flash",
                "https://generativelanguage.googleapis.com/v1beta",
                0.4,
                2048
        );

        String json = """
                {
                  "candidates": [
                    {
                      "finishReason": "MAX_TOKENS",
                      "content": {
                        "role": "model",
                        "parts": [
                          { "text": "Parte 1 " },
                          { "text": "e Parte 2" }
                        ]
                      }
                    }
                  ]
                }
                """;

        JsonNode root = objectMapper.readTree(json);
        assertEquals("Parte 1 e Parte 2", client.extractReplyText(root));
    }
}
