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
import org.mockito.ArgumentCaptor;
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

    @Test
    @DisplayName("Barreira 1: Deduplicação do Payload USDA - deve descartar itens clones com mesmo nome antes de gravar")
    void testDeduplicationUsdaPayload_OnlySavesFirstOccurrence() {
        when(foodRepository.findByNameContainingIgnoreCase("creatina")).thenReturn(new ArrayList<>());

        List<UsdaNutrient> nutrients = List.of(
                new UsdaNutrient(203, "203", "Protein", BigDecimal.ZERO, "G", null),
                new UsdaNutrient(205, "205", "Carbohydrate", BigDecimal.ZERO, "G", null),
                new UsdaNutrient(204, "204", "Fat", BigDecimal.ZERO, "G", null),
                new UsdaNutrient(1008, "208", "Energy", BigDecimal.ZERO, "KCAL", null)
        );

        // Dois itens da USDA com descrição e marca idênticas (mesmo nome gerado)
        UsdaFoodItem clone1 = new UsdaFoodItem(111L, "Creatine Monohydrate", "Creapure", null, "Supplements", "Branded", nutrients);
        UsdaFoodItem clone2 = new UsdaFoodItem(222L, "Creatine Monohydrate", "Creapure", null, "Supplements", "Branded", nutrients);
        // Terceiro item com nome diferente
        UsdaFoodItem unique3 = new UsdaFoodItem(333L, "Beta Alanine", "Generic", null, "Supplements", "Branded", nutrients);

        UsdaSearchResponse usdaResponse = new UsdaSearchResponse(3, 1, 1, List.of(clone1, clone2, unique3));
        when(usdaApiClient.searchFoods("creatina")).thenReturn(usdaResponse);
        when(foodRepository.findBySourceAndExternalId(any(), any())).thenReturn(Optional.empty());
        when(nutritionMathService.calculateDominantMacro(any(), any(), any())).thenReturn(DominantMacro.BALANCED);

        when(foodRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<Food> list = invocation.getArgument(0);
            return list.stream()
                    .map(f -> Food.builder()
                            .id(UUID.randomUUID())
                            .name(f.getName())
                            .source(f.getSource())
                            .build())
                    .toList();
        });

        List<Food> results = foodSyncService.searchFoods("creatina");

        // Deve ter salvo apenas 2 alimentos (clone1 e unique3; clone2 descartado)
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Food>> captor = ArgumentCaptor.forClass(List.class);
        verify(foodRepository).saveAll(captor.capture());
        assertEquals(2, captor.getValue().size());
        assertEquals(2, results.size());
    }

    @Test
    @DisplayName("Barreira 2: Proteção de Inserção (Idempotência Local) - não deve gravar se nome já existe localmente")
    void testIdempotencyLocal_DoesNotSaveUsdaFoodIfNameAlreadyExistsLocally() {
        Food existingLocal = Food.builder()
                .id(UUID.randomUUID())
                .name("Creatine Monohydrate (Creapure)")
                .source(FoodSource.USDA)
                .build();

        // Local tem apenas 1 item (< 3 itens, o que dispararia a USDA)
        when(foodRepository.findByNameContainingIgnoreCase("creatina")).thenReturn(new ArrayList<>(List.of(existingLocal)));

        UsdaFoodItem usdaItem = new UsdaFoodItem(
                999L,
                "Creatine Monohydrate",
                "Creapure",
                null,
                "Supplements",
                "Branded",
                Collections.emptyList()
        );

        when(usdaApiClient.searchFoods("creatina")).thenReturn(new UsdaSearchResponse(1, 1, 1, List.of(usdaItem)));

        List<Food> results = foodSyncService.searchFoods("creatina");

        // O item retornado pela USDA tem nome "Creatine Monohydrate (Creapure)", que já existia localmente.
        // Nenhuma gravação deve ocorrer
        verify(foodRepository, never()).saveAll(anyList());
        assertEquals(1, results.size());
        assertEquals(existingLocal.getId(), results.get(0).getId());
    }

    @Test
    @DisplayName("Barreira 3: Deduplicação da Resposta Final - garante nomes e IDs 100% únicos na resposta")
    void testFinalResponseDeduplication_ReturnsUniqueNamesAndIds() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        // Repositório retorna duplicatas (mesmo nome, IDs diferentes)
        Food f1 = Food.builder().id(id1).name("Ovo Cozido").build();
        Food f2 = Food.builder().id(id2).name("ovo cozido").build(); // Clone em minúsculas
        Food f3 = Food.builder().id(id1).name("Ovo Frito").build();   // Mesmo ID de f1
        Food f4 = Food.builder().id(UUID.randomUUID()).name("Batata Doce").build();

        when(foodRepository.findByNameContainingIgnoreCase("ovo")).thenReturn(List.of(f1, f2, f3, f4));

        List<Food> results = foodSyncService.searchFoods("ovo");

        // Deve retornar apenas f1 (primeiro 'Ovo Cozido') e f4 ('Batata Doce').
        // f2 tem o mesmo nome normalizado que f1 -> descartado.
        // f3 tem o mesmo ID de f1 -> descartado.
        assertEquals(2, results.size());
        assertEquals("Ovo Cozido", results.get(0).getName());
        assertEquals("Batata Doce", results.get(1).getName());
    }
}
