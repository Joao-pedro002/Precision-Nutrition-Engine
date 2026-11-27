package com.precisionnutrition.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.precisionnutrition.exception.AiServiceUnavailableException;
import com.precisionnutrition.model.dto.AiChatMessageDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiRestClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Deve concatenar todos os blocos de texto não-thought de candidates[0].content.parts")
    void testConcatenatesAllNonThoughtTextParts() throws Exception {
        GeminiRestClient client = new GeminiRestClient(
                objectMapper,
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
                objectMapper,
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

    @Test
    @DisplayName("Deve fazer parse com sucesso quando o Gemini retornar JSON válido com Content-Type: application/octet-stream")
    void testParsesValidJsonWithOctetStreamContentType() {
        String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
        RestClient.Builder builder = GeminiRestClient.configureRestClientBuilder(RestClient.builder(), baseUrl);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        GeminiRestClient client = new GeminiRestClient(
                restClient,
                objectMapper,
                "secret-test-key",
                "gemini-2.5-flash",
                0.4,
                2048
        );

        String responseJson = """
                {
                  "candidates": [
                    {
                      "finishReason": "STOP",
                      "content": {
                        "role": "model",
                        "parts": [
                          { "text": "Opção ideal: **150g de peito de frango** + **120g de arroz** (Proteínas completas)." }
                        ]
                      }
                    }
                  ]
                }
                """;

        server.expect(requestTo(baseUrl + "/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header("x-goog-api-key", "secret-test-key"))
                .andRespond(withSuccess(
                        responseJson.getBytes(StandardCharsets.UTF_8),
                        MediaType.APPLICATION_OCTET_STREAM
                ));

        String reply = client.generateContent(
                "Instrução do sistema",
                List.of(new AiChatMessageDto("user", "O que comer agora?"))
        );

        server.verify();
        assertEquals(
                "Opção ideal: **150g de peito de frango** + **120g de arroz** (Proteínas completas).",
                reply
        );
    }

    @Test
    @DisplayName("Deve lançar AiServiceUnavailableException quando corpo retornado não for JSON válido")
    void testThrowsAiServiceUnavailableOnInvalidJsonBody() {
        String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
        RestClient.Builder builder = GeminiRestClient.configureRestClientBuilder(RestClient.builder(), baseUrl);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        GeminiRestClient client = new GeminiRestClient(
                restClient,
                objectMapper,
                "secret-test-key",
                "gemini-2.5-flash",
                0.4,
                2048
        );

        server.expect(requestTo(baseUrl + "/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "<html>502 Bad Gateway</html>".getBytes(StandardCharsets.UTF_8),
                        MediaType.APPLICATION_OCTET_STREAM
                ));

        assertThrows(
                AiServiceUnavailableException.class,
                () -> client.generateContent("Sistema", List.of(new AiChatMessageDto("user", "Teste")))
        );
        server.verify();
    }
}
