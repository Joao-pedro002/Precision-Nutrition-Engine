package com.precisionnutrition.service;

import com.precisionnutrition.exception.FoodNotFoundException;
import com.precisionnutrition.model.dto.CustomFoodRequest;
import com.precisionnutrition.model.dto.FoodCreateRequest;
import com.precisionnutrition.model.dto.MicronutrientsData;
import com.precisionnutrition.model.entity.Food;
import com.precisionnutrition.model.enums.DominantMacro;
import com.precisionnutrition.model.enums.FoodSource;
import com.precisionnutrition.repository.FoodRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FoodServiceImpl implements FoodService {

    private final FoodRepository foodRepository;
    private final UsdaClientService usdaClientService;

    @Override
    @Transactional
    public Page<Food> searchFoods(String query, String source, String dominantMacro, boolean includeUsda, Pageable pageable) {
        String cleanQuery = (query != null) ? query.trim() : "";
        String cleanSource = (source != null && !source.isBlank()) ? source.trim().toUpperCase() : null;
        String cleanMacro = (dominantMacro != null && !dominantMacro.isBlank()) ? dominantMacro.trim().toUpperCase() : null;

        if (includeUsda && !cleanQuery.isBlank()) {
            try {
                // Sincroniza sob demanda e armazena em cache permanente no PostgreSQL
                usdaClientService.searchAndCacheUsdaFoods(cleanQuery, pageable.getPageSize());
            } catch (Exception e) {
                log.warn("Falha de fallback na consulta remota da USDA: {}", e.getMessage());
            }
        }

        return foodRepository.searchFuzzy(cleanQuery, cleanSource, cleanMacro, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Food getFoodById(UUID id) {
        return foodRepository.findById(id)
                .orElseThrow(() -> new FoodNotFoundException(id));
    }

    @Override
    @Transactional
    public Food createCustomFood(FoodCreateRequest request) {
        return createCustomFood(new CustomFoodRequest(
                request.name(),
                request.category(),
                request.dominantMacro(),
                request.energyKcal(),
                request.proteinG(),
                request.carbohydrateG(),
                request.lipidG(),
                request.fiberG(),
                request.micronutrients()
        ));
    }

    @Override
    @Transactional
    public Food createCustomFood(CustomFoodRequest request) {
        DominantMacro dominantMacro = request.dominantMacro();
        if (dominantMacro == null) {
            dominantMacro = autoClassify(request.proteinG(), request.carbohydrateG(), request.lipidG());
        }

        Food food = Food.builder()
                .name(request.name().trim())
                .source(FoodSource.CUSTOM)
                .category(request.category() != null && !request.category().isBlank() ? request.category().trim() : "Personalizados")
                .dominantMacro(dominantMacro)
                .servingSizeG(new BigDecimal("100.00"))
                .popularityScore(100)
                .energyKcal(request.energyKcal().setScale(2, RoundingMode.HALF_UP))
                .proteinG(request.proteinG().setScale(2, RoundingMode.HALF_UP))
                .carbohydrateG(request.carbohydrateG().setScale(2, RoundingMode.HALF_UP))
                .lipidG(request.lipidG().setScale(2, RoundingMode.HALF_UP))
                .fiberG(request.fiberG() != null ? request.fiberG().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                .micronutrients(request.micronutrients() != null ? request.micronutrients() : MicronutrientsData.empty())
                .build();

        return foodRepository.save(food);
    }

    @Override
    @Transactional
    public Food syncUsdaFood(Long fdcId) {
        return usdaClientService.importByFdcId(fdcId);
    }

    private DominantMacro autoClassify(BigDecimal p, BigDecimal c, BigDecimal f) {
        BigDecimal pKcal = p.multiply(new BigDecimal("4.00"));
        BigDecimal cKcal = c.multiply(new BigDecimal("4.00"));
        BigDecimal fKcal = f.multiply(new BigDecimal("9.00"));
        BigDecimal total = pKcal.add(cKcal).add(fKcal);

        if (total.compareTo(BigDecimal.ZERO) <= 0) return DominantMacro.BALANCED;

        BigDecimal pShare = pKcal.divide(total, 4, RoundingMode.HALF_UP);
        BigDecimal cShare = cKcal.divide(total, 4, RoundingMode.HALF_UP);
        BigDecimal fShare = fKcal.divide(total, 4, RoundingMode.HALF_UP);

        if (pShare.compareTo(new BigDecimal("0.40")) >= 0) return DominantMacro.PROTEIN;
        if (cShare.compareTo(new BigDecimal("0.50")) >= 0) return DominantMacro.CARB;
        if (fShare.compareTo(new BigDecimal("0.45")) >= 0) return DominantMacro.FAT;
        return DominantMacro.BALANCED;
    }
}
