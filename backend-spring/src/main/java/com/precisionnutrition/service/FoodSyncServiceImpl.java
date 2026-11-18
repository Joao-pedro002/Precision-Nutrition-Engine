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
                List<Food> newlyMappedFoods = new ArrayList<>();

                if (usdaResponse != null && !usdaResponse.safeFoods().isEmpty()) {
                    for (UsdaFoodItem item : usdaResponse.safeFoods()) {
                        String externalId = "USDA-" + item.fdcId();
                        Optional<Food> existing = foodRepository.findBySourceAndExternalId(FoodSource.USDA, externalId);

                        if (existing.isEmpty()) {
                            Food mappedFood = mapUsdaItemToFood(item, externalId);
                            newlyMappedFoods.add(mappedFood);
                        }
                    }

                    if (!newlyMappedFoods.isEmpty()) {
                        List<Food> savedFoods = foodRepository.saveAll(newlyMappedFoods);
                        log.info("Persistidos {} novos alimentos da USDA no banco de dados local.", savedFoods.size());

                        Set<UUID> existingIds = new HashSet<>();
                        for (Food f : localFoods) {
                            existingIds.add(f.getId());
                        }

                        for (Food sf : savedFoods) {
                            if (!existingIds.contains(sf.getId())) {
                                localFoods.add(sf);
                                existingIds.add(sf.getId());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Erro ao sincronizar com USDA FoodData Central: {}", e.getMessage());
            }
        }

        return localFoods;
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

        String name = item.description() != null ? item.description().trim() : "USDA Food #" + item.fdcId();
        if (item.brandOwner() != null && !item.brandOwner().isBlank() && !name.toLowerCase().contains(item.brandOwner().toLowerCase())) {
            name = name + " (" + item.brandOwner().trim() + ")";
        }

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
