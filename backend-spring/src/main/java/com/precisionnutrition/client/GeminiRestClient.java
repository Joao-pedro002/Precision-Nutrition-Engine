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
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
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

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final double temperature;
    private final int maxOutputTokens;

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
                maxOutputTokens
        );
    }

    GeminiRestClient(
            RestClient restClient,
            ObjectMapper objectMapper,
            String apiKey,
            String model,
            double temperature,
            int maxOutputTokens
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = (model != null && !model.isBlank()) ? model.trim() : "gemini-2.5-flash";
        this.temperature = temperature;
        this.maxOutputTokens = maxOutputTokens;
    }

    static RestClient.Builder configureRestClientBuilder(RestClient.Builder builder, String baseUrl) {
        String normalizedBaseUrl = baseUrl != null
                ? baseUrl.replaceAll("/+$", "")
                : "https://generativelanguage.googleapis.com/v1beta";

        return builder
                .baseUrl(normalizedBaseUrl)
                .messageConverters(converters -> {
                    converters.removeIf(StringHttpMessageConverter.class::isInstance);
                    converters.add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
                });
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

        try {
            String rawBody = restClient.post()
                    .uri("/models/{model}:generateContent", model)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                    .header("x-goog-api-key", apiKey)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

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
        } catch (HttpClientErrorException.TooManyRequests ex) {
            log.warn("Quota da API Gemini excedida (HTTP 429): {}", ex.getStatusText());
            throw new RateLimitExceededException(
                    "O assistente está temporariamente congestionado. Aguarde alguns instantes e tente novamente."
            );
        } catch (HttpClientErrorException ex) {
            log.error("Erro cliente ao chamar Gemini API (HTTP {}): tamanho={}",
                    ex.getStatusCode(), ex.getResponseBodyAsString().length());
            throw new AiServiceUnavailableException(
                    "Não foi possível consultar o assistente nutricional no momento. Verifique a configuração da IA.",
                    ex
            );
        } catch (RestClientException ex) {
            log.error("Falha de comunicação com a API Gemini: {}", ex.getMessage());
            throw new AiServiceUnavailableException(
                    "Serviço de IA temporariamente indisponível. Tente novamente em alguns segundos.",
                    ex
            );
        }
    }

    private String safeSnippet(String rawBody) {
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
