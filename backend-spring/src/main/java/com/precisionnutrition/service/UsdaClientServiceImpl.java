package com.precisionnutrition.service;

import com.precisionnutrition.exception.FoodNotFoundException;
import com.precisionnutrition.model.dto.MicronutrientsData;
import com.precisionnutrition.model.dto.UsdaFoodItem;
import com.precisionnutrition.model.dto.UsdaNutrient;
import com.precisionnutrition.model.dto.UsdaSearchResponse;
import com.precisionnutrition.model.entity.Food;
import com.precisionnutrition.model.enums.DominantMacro;
import com.precisionnutrition.model.enums.FoodSource;
import com.precisionnutrition.repository.FoodRepository;
import com.precisionnutrition.util.UsdaNutrientParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@Slf4j
public class UsdaClientServiceImpl implements UsdaClientService {

    private final FoodRepository foodRepository;
    private final RestClient restClient;
    private final String apiKey;

    public UsdaClientServiceImpl(
            FoodRepository foodRepository,
            @Value("${usda.api.base-url:https://api.nal.usda.gov/fdc/v1}") String baseUrl,
            @Value("${usda.api.key:DEMO_KEY}") String apiKey
    ) {
        this.foodRepository = foodRepository;
        this.apiKey = (apiKey != null && !apiKey.isBlank()) ? apiKey : "DEMO_KEY";
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Override
    @Transactional
    public List<Food> searchAndCacheUsdaFoods(String query, int pageSize) {
        if (query == null || query.trim().length() < 2) {
            return Collections.emptyList();
        }

        try {
            Map<String, Object> requestBody = Map.of(
                    "query", query.trim(),
                    "dataType", List.of("Foundation", "SR Legacy"),
                    "pageSize", Math.min(Math.max(pageSize, 1), 25)
            );

            log.info("Consultando USDA FoodData Central API para termo: '{}'...", query);

            UsdaSearchResponse response = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/foods/search")
                            .queryParam("api_key", apiKey)
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(UsdaSearchResponse.class);

            if (response == null || response.safeFoods().isEmpty()) {
                log.info("USDA FoodData Central retornou zero resultados para '{}'.", query);
                return Collections.emptyList();
            }

            List<Food> savedFoods = new ArrayList<>();
            for (UsdaFoodItem usdaItem : response.safeFoods()) {
                Food cached = mapAndUpsertFood(usdaItem);
                savedFoods.add(cached);
            }

            log.info("Importados e cacheados com sucesso {} alimentos da USDA.", savedFoods.size());
            return savedFoods;

        } catch (Exception e) {
            log.warn("Falha de rede ou rate limit ao comunicar com a USDA API (usando cache local): {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    @Transactional
    public Food importByFdcId(Long fdcId) {
        if (fdcId == null) {
            throw new IllegalArgumentException("fdcId não pode ser nulo.");
        }

        String externalId = "USDA-" + fdcId;
        Optional<Food> existing = foodRepository.findBySourceAndExternalId(FoodSource.USDA, externalId);
        if (existing.isPresent()) {
            return existing.get();
        }

        try {
            UsdaFoodItem item = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/food/{fdcId}")
                            .queryParam("api_key", apiKey)
                            .build(fdcId))
                    .retrieve()
                    .body(UsdaFoodItem.class);

            if (item == null) {
                throw new FoodNotFoundException("Alimento USDA não encontrado para fdcId: " + fdcId);
            }

            return mapAndUpsertFood(item);
        } catch (Exception e) {
            log.error("Erro ao buscar alimento específico da USDA com fdcId {}: {}", fdcId, e.getMessage());
            throw new FoodNotFoundException("Não foi possível importar alimento da USDA (ID: " + fdcId + "): " + e.getMessage());
        }
    }

    private Food mapAndUpsertFood(UsdaFoodItem item) {
        String externalId = "USDA-" + item.fdcId();
        Map<String, BigDecimal> nutrientMap = extractNutrients(item.safeNutrients());

        BigDecimal protein = nutrientMap.getOrDefault("203", BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal lipid = nutrientMap.getOrDefault("204", BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal carb = nutrientMap.getOrDefault("205", BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal energy = UsdaNutrientParser.extractEnergyKcal(item.safeNutrients());

        // Se a energia declarada estiver zerada, calcula Atwater
        if (energy.compareTo(BigDecimal.ZERO) <= 0 && (protein.compareTo(BigDecimal.ZERO) > 0 || carb.compareTo(BigDecimal.ZERO) > 0 || lipid.compareTo(BigDecimal.ZERO) > 0)) {
            energy = protein.multiply(new BigDecimal("4.00"))
                    .add(carb.multiply(new BigDecimal("4.00")))
                    .add(lipid.multiply(new BigDecimal("9.00")))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal fiber = nutrientMap.getOrDefault("291", BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        MicronutrientsData micros = new MicronutrientsData(
                nutrientMap.getOrDefault("301", BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP),
                nutrientMap.getOrDefault("303", BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP),
                nutrientMap.getOrDefault("304", BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP),
                nutrientMap.getOrDefault("306", BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP),
                nutrientMap.getOrDefault("307", BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP),
                nutrientMap.getOrDefault("309", BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP),
                nutrientMap.getOrDefault("401", BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP),
                nutrientMap.getOrDefault("418", BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP),
                nutrientMap.getOrDefault("601", BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
        );

        DominantMacro dominantMacro = classifyDominantMacro(protein, carb, lipid);

        Food food = foodRepository.findBySourceAndExternalId(FoodSource.USDA, externalId)
                .orElse(Food.builder()
                        .source(FoodSource.USDA)
                        .externalId(externalId)
                        .servingSizeG(new BigDecimal("100.00"))
                        .build());

        food.setName(item.description() != null ? item.description().trim() : "USDA Food #" + item.fdcId());
        food.setCategory(item.foodCategory() != null ? item.foodCategory().trim() : "USDA Imports");
        food.setDominantMacro(dominantMacro);
        food.setEnergyKcal(energy);
        food.setProteinG(protein);
        food.setCarbohydrateG(carb);
        food.setLipidG(lipid);
        food.setFiberG(fiber);
        food.setMicronutrients(micros);

        return foodRepository.save(food);
    }

    private Map<String, BigDecimal> extractNutrients(List<UsdaNutrient> nutrients) {
        Map<String, BigDecimal> map = new HashMap<>();
        for (UsdaNutrient n : nutrients) {
            if (n.nutrientNumber() != null && n.value() != null) {
                // Clamping de resíduos negativos analíticos
                BigDecimal val = n.value().compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : n.value();
                map.put(n.nutrientNumber().trim(), val);
            }
        }
        return map;
    }

    private DominantMacro classifyDominantMacro(BigDecimal p, BigDecimal c, BigDecimal f) {
        BigDecimal pKcal = p.multiply(new BigDecimal("4.00"));
        BigDecimal cKcal = c.multiply(new BigDecimal("4.00"));
        BigDecimal fKcal = f.multiply(new BigDecimal("9.00"));
        BigDecimal total = pKcal.add(cKcal).add(fKcal);

        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return DominantMacro.BALANCED;
        }

        BigDecimal pShare = pKcal.divide(total, 4, RoundingMode.HALF_UP);
        BigDecimal cShare = cKcal.divide(total, 4, RoundingMode.HALF_UP);
        BigDecimal fShare = fKcal.divide(total, 4, RoundingMode.HALF_UP);

        if (pShare.compareTo(new BigDecimal("0.40")) >= 0 || (pShare.compareTo(cShare) > 0 && pShare.compareTo(fShare) > 0 && pShare.compareTo(new BigDecimal("0.35")) >= 0)) {
            return DominantMacro.PROTEIN;
        } else if (cShare.compareTo(new BigDecimal("0.50")) >= 0 || (cShare.compareTo(pShare) > 0 && cShare.compareTo(fShare) > 0 && cShare.compareTo(new BigDecimal("0.45")) >= 0)) {
            return DominantMacro.CARB;
        } else if (fShare.compareTo(new BigDecimal("0.45")) >= 0 || (fShare.compareTo(pShare) > 0 && fShare.compareTo(cShare) > 0 && fShare.compareTo(new BigDecimal("0.40")) >= 0)) {
            return DominantMacro.FAT;
        } else {
            return DominantMacro.BALANCED;
        }
    }
}
