package com.precisionnutrition.service;

import com.precisionnutrition.model.dto.UsdaSearchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Collections;

@Service
@Slf4j
public class UsdaApiClientImpl implements UsdaApiClient {

    private final RestClient restClient;
    private final String apiKey;

    public UsdaApiClientImpl(
            @Value("${usda.api.base-url:https://api.nal.usda.gov/fdc/v1}") String baseUrl,
            @Value("${usda.api.key:DEMO_KEY}") String apiKey
    ) {
        this.apiKey = (apiKey != null && !apiKey.isBlank()) ? apiKey : "DEMO_KEY";
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Override
    public UsdaSearchResponse searchFoods(String query) {
        return searchFoods(query, 25);
    }

    @Override
    public UsdaSearchResponse searchFoods(String query, int pageSize) {
        if (query == null || query.trim().length() < 2) {
            return new UsdaSearchResponse(0, 0, 0, Collections.emptyList());
        }

        try {
            log.info("Executando chamada HTTP GET na USDA API para query='{}'...", query.trim());
            UsdaSearchResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/foods/search")
                            .queryParam("query", query.trim())
                            .queryParam("api_key", apiKey)
                            .queryParam("pageSize", Math.min(Math.max(pageSize, 1), 25))
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(UsdaSearchResponse.class);

            if (response == null || response.safeFoods().isEmpty()) {
                log.info("USDA API retornou 0 itens para query='{}'", query.trim());
                return new UsdaSearchResponse(0, 0, 0, Collections.emptyList());
            }

            return response;
        } catch (Exception e) {
            log.warn("Falha na chamada HTTP GET à USDA API [query='{}']: {}", query, e.getMessage());
            return new UsdaSearchResponse(0, 0, 0, Collections.emptyList());
        }
    }
}
