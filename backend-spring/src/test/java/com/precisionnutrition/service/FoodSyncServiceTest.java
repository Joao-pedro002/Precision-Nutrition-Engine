package com.precisionnutrition.service;

import com.precisionnutrition.model.dto.UsdaFoodItem;
import com.precisionnutrition.model.dto.UsdaNutrient;
import com.precisionnutrition.model.dto.UsdaSearchResponse;
import com.precisionnutrition.model.entity.Food;
import com.precisionnutrition.model.enums.DominantMacro;
import com.precisionnutrition.model.enums.FoodSource;
import com.precisionnutrition.repository.FoodRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FoodSyncServiceTest {

    @Mock
    private FoodRepository foodRepository;

    @Mock
    private UsdaApiClient usdaApiClient;

    @Mock
    private NutritionMathService nutritionMathService;

    @InjectMocks
    private FoodSyncServiceImpl foodSyncService;

    @Test
    @DisplayName("Read-Through Cache: Quando a busca local tiver >= 3 itens, não deve chamar a USDA API")
    void testLocalSearchSufficient_NoUsdaCall() {
        Food f1 = Food.builder().id(UUID.randomUUID()).name("Arroz Branco").build();
        Food f2 = Food.builder().id(UUID.randomUUID()).name("Arroz Integral").build();
        Food f3 = Food.builder().id(UUID.randomUUID()).name("Arroz Parboilizado").build();

        when(foodRepository.findByNameContainingIgnoreCase("arroz")).thenReturn(List.of(f1, f2, f3));

        List<Food> results = foodSyncService.searchFoods("arroz");

        assertEquals(3, results.size());
        verify(usdaApiClient, never()).searchFoods(anyString());
        verify(foodRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("Read-Through Cache: Quando a busca local tiver < 3 itens, deve chamar USDA, mapear e persistir")
    void testLocalSearchInsufficient_TriggersUsdaSync() {
        Food localWhey = Food.builder().id(UUID.randomUUID()).name("Whey Local").build();
        when(foodRepository.findByNameContainingIgnoreCase("whey")).thenReturn(new ArrayList<>(List.of(localWhey)));

        // Mock nutrients from USDA
        List<UsdaNutrient> nutrients = List.of(
                new UsdaNutrient(203, "203", "Protein", new BigDecimal("24.00"), "G", null),
                new UsdaNutrient(205, "205", "Carbohydrate, by difference", new BigDecimal("3.00"), "G", null),
                new UsdaNutrient(204, "204", "Total lipid (fat)", new BigDecimal("1.50"), "G", null),
                new UsdaNutrient(1008, "208", "Energy", new BigDecimal("120.00"), "KCAL", null)
        );

        UsdaFoodItem usdaItem = new UsdaFoodItem(
                999888L,
                "Gold Standard 100% Whey",
                "Optimum Nutrition",
                null,
                "Supplements",
                "Branded",
                nutrients
        );

        UsdaSearchResponse usdaResponse = new UsdaSearchResponse(1, 1, 1, List.of(usdaItem));
        when(usdaApiClient.searchFoods("whey")).thenReturn(usdaResponse);
        when(foodRepository.findBySourceAndExternalId(FoodSource.USDA, "USDA-999888")).thenReturn(Optional.empty());
        when(nutritionMathService.calculateDominantMacro(any(), any(), any())).thenReturn(DominantMacro.PROTEIN);

        Food savedUsdaFood = Food.builder()
                .id(UUID.randomUUID())
                .name("Gold Standard 100% Whey (Optimum Nutrition)")
                .source(FoodSource.USDA)
                .externalId("USDA-999888")
                .servingSizeG(new BigDecimal("100.00"))
                .energyKcal(new BigDecimal("120.00"))
                .proteinG(new BigDecimal("24.00"))
                .carbohydrateG(new BigDecimal("3.00"))
                .lipidG(new BigDecimal("1.50"))
                .dominantMacro(DominantMacro.PROTEIN)
                .build();

        when(foodRepository.saveAll(anyList())).thenReturn(List.of(savedUsdaFood));

        List<Food> results = foodSyncService.searchFoods("whey");

        assertEquals(2, results.size());
        verify(usdaApiClient, times(1)).searchFoods("whey");
        verify(foodRepository, times(1)).saveAll(anyList());

        Food imported = results.get(1);
        assertEquals(FoodSource.USDA, imported.getSource());
        assertEquals(new BigDecimal("100.00"), imported.getServingSizeG());
        assertEquals(new BigDecimal("24.00"), imported.getProteinG());
    }
}
