package com.precisionnutrition.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.precisionnutrition.model.dto.MicronutrientsData;
import com.precisionnutrition.model.entity.Food;
import com.precisionnutrition.model.enums.DominantMacro;
import com.precisionnutrition.model.enums.FoodSource;
import com.precisionnutrition.repository.FoodRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Carregador oficial e idempotente da Tabela TACO 4ª Edição.
 * Executa em TODOS os perfis (incluindo prod e dev) garantindo catálogo local sempre disponível.
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class TacoCatalogSeeder implements ApplicationRunner {

    private static final String CATALOG_RESOURCE_PATH = "data/taco_catalog.json";

    private final FoodRepository foodRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedTacoCatalog();
    }

    public int seedTacoCatalog() {
        ClassPathResource resource = new ClassPathResource(CATALOG_RESOURCE_PATH);
        if (!resource.exists()) {
            log.warn("Recurso de catálogo TACO não encontrado no classpath: {}", CATALOG_RESOURCE_PATH);
            return 0;
        }

        try (InputStream is = resource.getInputStream()) {
            List<TacoSeedEntry> catalog = objectMapper.readValue(is, new TypeReference<>() {});
            if (catalog == null || catalog.isEmpty()) {
                return 0;
            }

            Set<String> existingExternalIds = foodRepository.findExternalIdsBySource(FoodSource.TACO);
            if (existingExternalIds.size() >= catalog.size()) {
                log.info("Catálogo TACO já completo no banco ({} alimentos). Seed ignorado (idempotente).", existingExternalIds.size());
                return 0;
            }

            List<Food> toInsert = new ArrayList<>();
            for (TacoSeedEntry entry : catalog) {
                if (entry.externalId() == null || existingExternalIds.contains(entry.externalId())) {
                    continue;
                }

                DominantMacro dominantMacro = parseDominantMacro(entry.dominantMacro());
                Food food = Food.builder()
                        .name(entry.name() != null ? entry.name().trim() : "Alimento TACO")
                        .source(FoodSource.TACO)
                        .externalId(entry.externalId())
                        .category(entry.category() != null ? entry.category().trim() : "TACO")
                        .dominantMacro(dominantMacro)
                        .servingSizeG(safeScale(entry.servingSizeG(), new BigDecimal("100.00")))
                        .popularityScore(entry.popularityScore() != null ? entry.popularityScore() : 30)
                        .energyKcal(safeScale(entry.energyKcal(), BigDecimal.ZERO))
                        .proteinG(safeScale(entry.proteinG(), BigDecimal.ZERO))
                        .carbohydrateG(safeScale(entry.carbohydrateG(), BigDecimal.ZERO))
                        .lipidG(safeScale(entry.lipidG(), BigDecimal.ZERO))
                        .fiberG(safeScale(entry.fiberG(), BigDecimal.ZERO))
                        .micronutrients(entry.micronutrients() != null ? entry.micronutrients() : MicronutrientsData.empty())
                        .build();

                toInsert.add(food);
                existingExternalIds.add(entry.externalId());
            }

            if (!toInsert.isEmpty()) {
                foodRepository.saveAll(toInsert);
                log.info("Catálogo TACO carregado com sucesso: {} novos alimentos inseridos (total TACO: {}).",
                        toInsert.size(), existingExternalIds.size());
            }

            return toInsert.size();
        } catch (Exception e) {
            log.error("Falha ao carregar catálogo TACO a partir de {}: {}", CATALOG_RESOURCE_PATH, e.getMessage(), e);
            return 0;
        }
    }

    private BigDecimal safeScale(BigDecimal value, BigDecimal fallback) {
        BigDecimal effective = (value != null && value.compareTo(BigDecimal.ZERO) >= 0) ? value : fallback;
        return effective.setScale(2, RoundingMode.HALF_UP);
    }

    private DominantMacro parseDominantMacro(String raw) {
        if (raw == null || raw.isBlank()) {
            return DominantMacro.BALANCED;
        }
        try {
            return DominantMacro.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return DominantMacro.BALANCED;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TacoSeedEntry(
            @JsonProperty("external_id") String externalId,
            @JsonProperty("name") String name,
            @JsonProperty("category") String category,
            @JsonProperty("dominant_macro") String dominantMacro,
            @JsonProperty("serving_size_g") BigDecimal servingSizeG,
            @JsonProperty("popularity_score") Integer popularityScore,
            @JsonProperty("energy_kcal") BigDecimal energyKcal,
            @JsonProperty("protein_g") BigDecimal proteinG,
            @JsonProperty("carbohydrate_g") BigDecimal carbohydrateG,
            @JsonProperty("lipid_g") BigDecimal lipidG,
            @JsonProperty("fiber_g") BigDecimal fiberG,
            @JsonProperty("micronutrients") MicronutrientsData micronutrients
    ) {}
}
