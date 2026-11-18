package com.precisionnutrition.service;

import com.precisionnutrition.model.dto.MicronutrientsData;
import com.precisionnutrition.model.dto.UsdaFoodItem;
import com.precisionnutrition.model.dto.UsdaNutrient;
import com.precisionnutrition.model.dto.UsdaSearchResponse;
import com.precisionnutrition.model.entity.Food;
import com.precisionnutrition.model.enums.DominantMacro;
import com.precisionnutrition.model.enums.FoodSource;
import com.precisionnutrition.repository.FoodRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FoodSyncServiceImpl implements FoodSyncService {

    private final FoodRepository foodRepository;
    private final UsdaApiClient usdaApiClient;
    private final NutritionMathService nutritionMathService;

    @Override
    @Transactional
    public List<Food> searchFoods(String query) {
        if (query == null || query.trim().length() < 2) {
            return Collections.emptyList();
        }

        String cleanQuery = query.trim();

        // 1. Busca Local
        List<Food> localFoods = new ArrayList<>(foodRepository.findByNameContainingIgnoreCase(cleanQuery));
        log.info("Busca local para '{}' retornou {} item(ns).", cleanQuery, localFoods.size());

        // 2. Fallback para USDA se vazio ou menos de 3 itens
        if (localFoods.size() < 3) {
            log.info("Resultado local inferior a 3 itens para '{}'. Disparando busca Read-Through na USDA API...", cleanQuery);
            try {
                UsdaSearchResponse usdaResponse = usdaApiClient.searchFoods(cleanQuery);

                if (usdaResponse != null && !usdaResponse.safeFoods().isEmpty()) {
                    // 1. Deduplicação do Payload da USDA (Pré-Gravação): Manter apenas a primeira ocorrência de cada nome único
                    Map<String, UsdaFoodItem> uniqueUsdaPayload = new LinkedHashMap<>();
                    for (UsdaFoodItem item : usdaResponse.safeFoods()) {
                        String nameKey = buildFoodName(item).toLowerCase().trim();
                        uniqueUsdaPayload.putIfAbsent(nameKey, item);
                    }

                    // 2. Proteção de Inserção (Idempotência Local): Apenas gravar se NÃO existir no repositório local
                    Set<String> localNames = localFoods.stream()
                            .filter(Objects::nonNull)
                            .map(Food::getName)
                            .filter(n -> n != null && !n.isBlank())
                            .map(n -> n.toLowerCase().trim())
                            .collect(Collectors.toSet());

                    List<Food> newFoods = new ArrayList<>();
                    for (Map.Entry<String, UsdaFoodItem> entry : uniqueUsdaPayload.entrySet()) {
                        String nameKey = entry.getKey();
                        UsdaFoodItem item = entry.getValue();

                        // Ignora se o nome já existe na base local
                        if (localNames.contains(nameKey)) {
                            log.debug("Alimento USDA descartado por já existir na base local: {}", nameKey);
                            continue;
                        }

                        String externalId = "USDA-" + item.fdcId();
                        Optional<Food> existing = foodRepository.findBySourceAndExternalId(FoodSource.USDA, externalId);

                        if (existing.isEmpty()) {
                            Food mappedFood = mapUsdaItemToFood(item, externalId);
                            newFoods.add(mappedFood);
                            localNames.add(nameKey); // Proteção contra repetição dentro do mesmo lote
                        }
                    }

                    if (!newFoods.isEmpty()) {
                        List<Food> savedFoods = foodRepository.saveAll(newFoods);
                        log.info("Persistidos {} novos alimentos da USDA no banco de dados local.", savedFoods.size());
                        localFoods.addAll(savedFoods);
                    }
                }
            } catch (Exception e) {
                log.warn("Erro ao sincronizar com USDA FoodData Central: {}", e.getMessage());
            }
        }

        // 3. Deduplicação da Resposta Final (Controller Response): Nomes e IDs 100% únicos
        return localFoods.stream()
                .filter(Objects::nonNull)
                .filter(f -> f.getName() != null && !f.getName().isBlank())
                .filter(distinctByKey(Food::getId))
                .filter(distinctByKey(f -> f.getName().toLowerCase().trim()))
                .toList();
    }

    /**
     * Predicado utilitário para deduplicação em Streams por chave de extração.
     */
    public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> {
            if (t == null) return false;
            Object key = keyExtractor.apply(t);
            if (key == null) return true;
            return seen.add(key);
        };
    }

    /**
     * Constrói o nome padronizado do alimento USDA, incluindo a marca se aplicável.
     */
    public String buildFoodName(UsdaFoodItem item) {
        if (item == null) {
            return "USDA Food";
        }
        String name = item.description() != null ? item.description().trim() : "USDA Food #" + item.fdcId();
        if (item.brandOwner() != null && !item.brandOwner().isBlank() && !name.toLowerCase().contains(item.brandOwner().toLowerCase())) {
            name = name + " (" + item.brandOwner().trim() + ")";
        }
        return name;
    }

    private Food mapUsdaItemToFood(UsdaFoodItem item, String externalId) {
        List<UsdaNutrient> nutrients = item.safeNutrients();

        // Mapeamento dos Nutrientes exatos da USDA
        // Proteína: ID 203 (ou 1003 ou número 203)
        BigDecimal protein = extractNutrientValue(nutrients, 203, "203", 1003, "Protein");
        // Carboidratos: ID 205 (ou 1005 ou número 205)
        BigDecimal carb = extractNutrientValue(nutrients, 205, "205", 1005, "Carbohydrate");
        // Lipídios: ID 204 (ou 1004 ou número 204)
        BigDecimal lipid = extractNutrientValue(nutrients, 204, "204", 1004, "Total lipid (fat)");
        // Energia: ID 1008 (ou 208 ou número 208)
        BigDecimal energy = extractNutrientValue(nutrients, 1008, "208", 208, "Energy");
        // Fibras: ID 291 (ou 1079 ou número 291)
        BigDecimal fiber = extractNutrientValue(nutrients, 291, "291", 1079, "Fiber");

        // Se a energia declarada estiver zerada mas houver macros, aplica Atwater
        if (energy.compareTo(BigDecimal.ZERO) <= 0 && (protein.compareTo(BigDecimal.ZERO) > 0 || carb.compareTo(BigDecimal.ZERO) > 0 || lipid.compareTo(BigDecimal.ZERO) > 0)) {
            energy = protein.multiply(new BigDecimal("4.00"))
                    .add(carb.multiply(new BigDecimal("4.00")))
                    .add(lipid.multiply(new BigDecimal("9.00")))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        DominantMacro dominantMacro = nutritionMathService.calculateDominantMacro(protein, carb, lipid);

        // Extração de micronutrientes
        MicronutrientsData micros = new MicronutrientsData(
                extractNutrientValue(nutrients, 301, "301", 1087, "Calcium"),
                extractNutrientValue(nutrients, 303, "303", 1089, "Iron"),
                extractNutrientValue(nutrients, 304, "304", 1090, "Magnesium"),
                extractNutrientValue(nutrients, 306, "306", 1092, "Potassium"),
                extractNutrientValue(nutrients, 307, "307", 1093, "Sodium"),
                extractNutrientValue(nutrients, 309, "309", 1095, "Zinc"),
                extractNutrientValue(nutrients, 401, "401", 1162, "Vitamin C"),
                extractNutrientValue(nutrients, 418, "418", 1178, "Vitamin B-12"),
                extractNutrientValue(nutrients, 601, "601", 1258, "Cholesterol")
        );

        String name = buildFoodName(item);

        return Food.builder()
                .name(name)
                .source(FoodSource.USDA)
                .externalId(externalId)
                .category(item.foodCategory() != null ? item.foodCategory().trim() : (item.brandOwner() != null ? item.brandOwner().trim() : "USDA Imports"))
                .dominantMacro(dominantMacro)
                .servingSizeG(new BigDecimal("100.00")) // baseAmount = 100g
                .energyKcal(energy.setScale(2, RoundingMode.HALF_UP))
                .proteinG(protein.setScale(2, RoundingMode.HALF_UP))
                .carbohydrateG(carb.setScale(2, RoundingMode.HALF_UP))
                .lipidG(lipid.setScale(2, RoundingMode.HALF_UP))
                .fiberG(fiber.setScale(2, RoundingMode.HALF_UP))
                .micronutrients(micros)
                .build();
    }

    private BigDecimal extractNutrientValue(List<UsdaNutrient> nutrients, Integer targetId1, String targetNumber, Integer targetId2, String nameSub) {
        if (nutrients == null) return BigDecimal.ZERO;

        for (UsdaNutrient n : nutrients) {
            boolean idMatch = (targetId1 != null && targetId1.equals(n.nutrientId()))
                    || (targetId2 != null && targetId2.equals(n.nutrientId()));

            boolean numberMatch = targetNumber != null && targetNumber.equalsIgnoreCase(n.nutrientNumber());

            boolean nameMatch = nameSub != null && n.nutrientName() != null && n.nutrientName().toLowerCase().contains(nameSub.toLowerCase());

            if (idMatch || numberMatch || nameMatch) {
                if (n.value() != null) {
                    BigDecimal val = n.value().compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : n.value();
                    return val.setScale(2, RoundingMode.HALF_UP);
                }
            }
        }

        return BigDecimal.ZERO;
    }
}
