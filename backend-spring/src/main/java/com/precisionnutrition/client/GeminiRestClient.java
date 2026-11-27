package com.precisionnutrition.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.precisionnutrition.exception.AiServiceUnavailableException;
import com.precisionnutrition.exception.RateLimitExceededException;
import com.precisionnutrition.model.dto.AiChatMessageDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class GeminiRestClient implements GeminiClient {

    private static final int MAX_LOG_SNIPPET_LENGTH = 200;
    private static final long DEFAULT_RETRY_DELAY_MS = 1500L;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final double temperature;
    private final int maxOutputTokens;
    private final long retryDelayMs;

    private record RawHttpResponse(HttpStatusCode status, String body) {}

    @Autowired
    public GeminiRestClient(
            ObjectMapper objectMapper,
            @Value("${app.ai.gemini.api-key:}") String apiKey,
            @Value("${app.ai.gemini.model:gemini-2.5-flash}") String model,
            @Value("${app.ai.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
            @Value("${app.ai.gemini.temperature:0.4}") double temperature,
            @Value("${app.ai.gemini.max-output-tokens:2048}") int maxOutputTokens
    ) {
        this(
                buildDefaultRestClient(baseUrl),
                objectMapper,
                apiKey,
                model,
                temperature,
                maxOutputTokens,
                DEFAULT_RETRY_DELAY_MS
        );
    }

    GeminiRestClient(
            RestClient restClient,
            ObjectMapper objectMapper,
            String apiKey,
            String model,
            double temperature,
            int maxOutputTokens,
            long retryDelayMs
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = (model != null && !model.isBlank()) ? model.trim() : "gemini-2.5-flash";
        this.temperature = temperature;
        this.maxOutputTokens = maxOutputTokens;
        this.retryDelayMs = Math.max(0L, retryDelayMs);
    }

    static RestClient.Builder configureRestClientBuilder(RestClient.Builder builder, String baseUrl) {
        String normalizedBaseUrl = baseUrl != null
                ? baseUrl.replaceAll("/+$", "")
                : "https://generativelanguage.googleapis.com/v1beta";

        return builder.baseUrl(normalizedBaseUrl);
    }

    private static RestClient buildDefaultRestClient(String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(60));

        return configureRestClientBuilder(RestClient.builder(), baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    @Override
    public String getModel() {
        return model;
    }

    @Override
    public String generateContent(String systemInstruction, List<AiChatMessageDto> turns) {
        if (!isConfigured()) {
            throw new AiServiceUnavailableException(
                    "O Copiloto Nutricional com IA ainda não está configurado neste servidor (GEMINI_API_KEY ausente)."
            );
        }

        Map<String, Object> payload = buildRequestPayload(systemInstruction, turns);
        int maxAttempts = 2; // 1 tentativa inicial + 1 retentativa automática em HTTP 503

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            RawHttpResponse rawResponse = executeRawPost(payload);
            HttpStatusCode status = rawResponse.status();
            String rawBody = rawResponse.body();

            if (status.value() == HttpStatus.SERVICE_UNAVAILABLE.value() && attempt < maxAttempts) {
                log.warn(
                        "Gemini retornou HTTP 503 (alta demanda temporária). Retentando em {}ms (tentativa {}/{}, modelo={}, trecho='{}')",
                        retryDelayMs,
                        attempt,
                        maxAttempts,
                        model,
                        safeSnippet(rawBody)
                );
                sleepBeforeRetry();
                continue;
            }

            if (status.value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
                log.warn(
                        "Quota da API Gemini excedida (HTTP 429, modelo={}, tamanho={}, trecho='{}')",
                        model,
                        rawBody.length(),
                        safeSnippet(rawBody)
                );
                throw new RateLimitExceededException(
                        "O assistente está temporariamente congestionado. Aguarde alguns instantes e tente novamente."
                );
            }

            if (status.is4xxClientError()) {
                log.error(
                        "Erro cliente ao chamar Gemini API (HTTP {}, modelo={}, tamanho={}, trecho='{}')",
                        status.value(),
                        model,
                        rawBody.length(),
                        safeSnippet(rawBody)
                );
                throw new AiServiceUnavailableException(
                        "Não foi possível consultar o assistente nutricional no momento. Verifique a configuração da IA."
                );
            }

            if (status.is5xxServerError()) {
                log.error(
                        "Erro servidor ao chamar Gemini API (HTTP {}, modelo={}, tamanho={}, trecho='{}')",
                        status.value(),
                        model,
                        rawBody.length(),
                        safeSnippet(rawBody)
                );
                throw new AiServiceUnavailableException(
                        "Serviço de IA temporariamente indisponível devido à alta demanda. Tente novamente em instantes."
                );
            }

            return parseAndExtractReply(rawBody);
        }

        throw new AiServiceUnavailableException(
                "Serviço de IA temporariamente indisponível. Tente novamente em alguns segundos."
        );
    }

    private RawHttpResponse executeRawPost(Map<String, Object> payload) {
        try {
            RawHttpResponse result = restClient.post()
                    .uri("/models/{model}:generateContent", model)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                    .header("x-goog-api-key", apiKey)
                    .body(payload)
                    .exchange((request, response) -> {
                        HttpStatusCode status = response.getStatusCode();
                        byte[] bytes = response.getBody().readAllBytes();
                        String rawBody = new String(bytes, StandardCharsets.UTF_8);
                        return new RawHttpResponse(status, rawBody);
                    });

            if (result == null) {
                throw new AiServiceUnavailableException(
                        "A IA retornou uma resposta vazia no momento. Tente novamente em instantes."
                );
            }
            return result;
        } catch (AiServiceUnavailableException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.error("Falha de comunicação com a API Gemini (modelo={}): {}", model, ex.getMessage());
            throw new AiServiceUnavailableException(
                    "Serviço de IA temporariamente indisponível. Tente novamente em alguns segundos.",
                    ex
            );
        }
    }

    private String parseAndExtractReply(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            log.error("Gemini retornou corpo vazio/nulo (modelo={}, tamanho=0)", model);
            throw new AiServiceUnavailableException(
                    "A IA retornou uma resposta vazia no momento. Tente novamente em instantes."
            );
        }

        JsonNode response;
        try {
            response = objectMapper.readTree(rawBody);
        } catch (JsonProcessingException ex) {
            log.error(
                    "Falha ao fazer parse do JSON retornado pelo Gemini (modelo={}, tamanho={}, trecho='{}')",
                    model,
                    rawBody.length(),
                    safeSnippet(rawBody),
                    ex
            );
            throw new AiServiceUnavailableException(
                    "Não foi possível interpretar a resposta da IA no momento. Tente novamente.",
                    ex
            );
        }

        String text = extractReplyText(response);
        if (text.isBlank()) {
            log.warn("Gemini retornou resposta sem blocos de texto visíveis (modelo={}, tamanho={})",
                    model, rawBody.length());
            throw new AiServiceUnavailableException(
                    "A IA não conseguiu gerar uma resposta no momento. Tente reformular sua pergunta."
            );
        }
        return text;
    }

    private void sleepBeforeRetry() {
        if (retryDelayMs <= 0L) {
            return;
        }
        try {
            Thread.sleep(retryDelayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiServiceUnavailableException(
                    "Consulta ao assistente nutricional interrompida. Tente novamente.",
                    ex
            );
        }
    }

    private String safeSnippet(String rawBody) {
        if (rawBody == null || rawBody.isEmpty()) {
            return "";
        }
        String sanitized = rawBody.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (!apiKey.isBlank()) {
            sanitized = sanitized.replace(apiKey, "[REDACTED]");
        }
        if (sanitized.length() <= MAX_LOG_SNIPPET_LENGTH) {
            return sanitized;
        }
        return sanitized.substring(0, MAX_LOG_SNIPPET_LENGTH) + "...";
    }

    private Map<String, Object> buildRequestPayload(String systemInstruction, List<AiChatMessageDto> turns) {
        Map<String, Object> payload = new LinkedHashMap<>();

        if (systemInstruction != null && !systemInstruction.isBlank()) {
            payload.put("systemInstruction", Map.of(
                    "parts", List.of(Map.of("text", systemInstruction))
            ));
        }

        List<Map<String, Object>> contents = new ArrayList<>();
        for (AiChatMessageDto turn : turns) {
            contents.add(Map.of(
                    "role", turn.role(),
                    "parts", List.of(Map.of("text", turn.text()))
            ));
        }
        payload.put("contents", contents);

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", temperature);
        generationConfig.put("maxOutputTokens", maxOutputTokens);
        payload.put("generationConfig", generationConfig);

        return payload;
    }

    String extractReplyText(JsonNode response) {
        if (response == null) {
            return "";
        }
        JsonNode candidates = response.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            return "";
        }

        JsonNode firstCandidate = candidates.get(0);
        String finishReason = firstCandidate.path("finishReason").asText("");
        if (!finishReason.isBlank() && !"STOP".equalsIgnoreCase(finishReason)) {
            log.warn(
                    "Resposta do Gemini finalizada com finishReason='{}' (modelo={}, maxOutputTokens={})",
                    finishReason,
                    model,
                    maxOutputTokens
            );
        }

        JsonNode parts = firstCandidate.path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (JsonNode part : parts) {
            if (part.path("thought").asBoolean(false)) {
                continue;
            }
            if (!part.hasNonNull("text")) {
                continue;
            }
            String text = part.path("text").asText("");
            if (!text.isEmpty()) {
                sb.append(text);
            }
        }
        return sb.toString().trim();
    }
}
