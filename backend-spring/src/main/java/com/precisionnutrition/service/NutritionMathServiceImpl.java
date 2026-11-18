package com.precisionnutrition.service;

import com.precisionnutrition.exception.FoodNotFoundException;
import com.precisionnutrition.exception.IncompatibleFoodSubstitutionException;
import com.precisionnutrition.model.dto.*;
import com.precisionnutrition.model.entity.Food;
import com.precisionnutrition.model.enums.CalorieZone;
import com.precisionnutrition.model.enums.DominantMacro;
import com.precisionnutrition.model.enums.MacroTarget;
import com.precisionnutrition.repository.FoodRepository;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@NoArgsConstructor
public class NutritionMathServiceImpl implements NutritionMathService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");
    private static final BigDecimal FOUR = new BigDecimal("4.00");
    private static final BigDecimal NINE = new BigDecimal("9.00");
    private static final BigDecimal FIVE = new BigDecimal("5.00");
    private static final BigDecimal MIN_MACRO_THRESHOLD = new BigDecimal("0.50");
    private static final BigDecimal MIN_KCAL_THRESHOLD = new BigDecimal("10.00");
    private static final BigDecimal DRIFT_PERCENT_THRESHOLD = new BigDecimal("10.00");
    private static final BigDecimal ATWATER_TOLERANCE = new BigDecimal("0.15");

    @Autowired(required = false)
    private FoodRepository foodRepository;

    public NutritionMathServiceImpl(FoodRepository foodRepository) {
        this.foodRepository = foodRepository;
    }

    @Override
    public BigDecimal scale100g(BigDecimal valuePer100g, BigDecimal weightG) {
        if (valuePer100g == null || weightG == null || weightG.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return valuePer100g
                .divide(ONE_HUNDRED, 6, RoundingMode.HALF_UP)
                .multiply(weightG)
                .setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public FoodSummary calculatePortionMacros(Food food, BigDecimal weightG) {
        if (food == null || weightG == null) {
            throw new IllegalArgumentException("Alimento e peso não podem ser nulos para cálculo de porção.");
        }
        return new FoodSummary(
                food.getId(),
                food.getName(),
                weightG.setScale(2, RoundingMode.HALF_UP),
                scale100g(food.getEnergyKcal(), weightG),
                scale100g(food.getProteinG(), weightG),
                scale100g(food.getCarbohydrateG(), weightG),
                scale100g(food.getLipidG(), weightG),
                scale100g(food.getFiberG(), weightG)
        );
    }

    @Override
    public MicronutrientsData calculatePortionMicronutrients(MicronutrientsData micros100g, BigDecimal weightG) {
        if (micros100g == null || weightG == null) {
            return MicronutrientsData.empty();
        }
        return new MicronutrientsData(
                scale100g(micros100g.safeCalcium(), weightG),
                scale100g(micros100g.safeIron(), weightG),
                scale100g(micros100g.safeMagnesium(), weightG),
                scale100g(micros100g.safePotassium(), weightG),
                scale100g(micros100g.safeSodium(), weightG),
                scale100g(micros100g.safeZinc(), weightG),
                scale100g(micros100g.safeVitaminC(), weightG),
                scale100g(micros100g.safeVitaminB12(), weightG),
                scale100g(micros100g.safeCholesterol(), weightG)
        );
    }

    @Override
    public NutrientAuditResult auditAtwaterDivergence(BigDecimal energyKcal, BigDecimal proteinG, BigDecimal carbG, BigDecimal lipidG) {
        BigDecimal p = proteinG != null ? proteinG : BigDecimal.ZERO;
        BigDecimal c = carbG != null ? carbG : BigDecimal.ZERO;
        BigDecimal l = lipidG != null ? lipidG : BigDecimal.ZERO;
        BigDecimal official = energyKcal != null ? energyKcal : BigDecimal.ZERO;

        BigDecimal atwaterKcal = p.multiply(FOUR)
                .add(c.multiply(FOUR))
                .add(l.multiply(NINE))
                .setScale(2, RoundingMode.HALF_UP);

        if (official.compareTo(BigDecimal.ZERO) <= 0) {
            return new NutrientAuditResult(
                    official,
                    atwaterKcal,
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    false,
                    "Alimento com energia declarada zero ou não informada."
            );
        }

        BigDecimal diff = official.subtract(atwaterKcal).abs();
        BigDecimal divergencePercent = diff.divide(official, 4, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED)
                .setScale(2, RoundingMode.HALF_UP);

        boolean isDivergent = diff.divide(official, 4, RoundingMode.HALF_UP).compareTo(ATWATER_TOLERANCE) > 0;
        String notes = isDivergent
                ? String.format("Atenção: Divergência de Atwater de %s%% excede a tolerância de 15.0%%.", divergencePercent)
                : "Conforme laudo físico-químico dentro da tolerância de Atwater.";

        return new NutrientAuditResult(official, atwaterKcal, divergencePercent, isDivergent, notes);
    }

    @Override
    public NutrientAuditResult auditAtwaterEnergy(Food food) {
        if (food == null) {
            throw new IllegalArgumentException("Alimento não pode ser nulo para auditoria de Atwater.");
        }
        return auditAtwaterDivergence(food.getEnergyKcal(), food.getProteinG(), food.getCarbohydrateG(), food.getLipidG());
    }

    @Override
    public BigDecimal calculateSodiumPotassiumRatio(BigDecimal sodiumMg, BigDecimal potassiumMg) {
        if (sodiumMg == null || potassiumMg == null || potassiumMg.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return sodiumMg.divide(potassiumMg, 2, RoundingMode.HALF_UP);
    }

    @Override
    public CaloricZoneResult determineCaloricZone(BigDecimal consumedKcal, BigDecimal targetKcalMin, BigDecimal targetKcalMax, BigDecimal maintenanceKcalCeiling) {
        BigDecimal consumed = consumedKcal != null ? consumedKcal : BigDecimal.ZERO;
        BigDecimal min = targetKcalMin != null ? targetKcalMin : BigDecimal.ZERO;
        BigDecimal max = targetKcalMax != null ? targetKcalMax : min;
        BigDecimal ceiling = maintenanceKcalCeiling != null ? maintenanceKcalCeiling : max;

        CalorieZone zone;
        if (consumed.compareTo(min) < 0) {
            zone = CalorieZone.BELOW_TARGET;
        } else if (consumed.compareTo(max) <= 0) {
            zone = CalorieZone.OPTIMAL_DEFICIT;
        } else if (consumed.compareTo(ceiling) <= 0) {
            zone = CalorieZone.MAINTENANCE_MARGIN;
        } else {
            zone = CalorieZone.SURPLUS;
        }

        BigDecimal deltaToTargetMin = min.subtract(consumed).setScale(2, RoundingMode.HALF_UP);
        BigDecimal deltaToMaintenanceCeiling = ceiling.subtract(consumed).setScale(2, RoundingMode.HALF_UP);

        return new CaloricZoneResult(consumed, min, max, ceiling, zone, deltaToTargetMin, deltaToMaintenanceCeiling);
    }

    @Override
    public CaloricZoneResult evaluateCaloricZone(BigDecimal consumedKcal, BigDecimal targetKcalMin, BigDecimal targetKcalMax, BigDecimal maintenanceKcalCeiling) {
        return determineCaloricZone(consumedKcal, targetKcalMin, targetKcalMax, maintenanceKcalCeiling);
    }

    @Override
    public SubstitutionResult calculateEquivalentSubstitution(
            Food sourceFood,
            BigDecimal sourceWeightG,
            Food targetFood,
            MacroTarget targetMacroOverride
    ) {
        if (sourceFood == null || targetFood == null) {
            throw new IllegalArgumentException("Alimentos de origem e destino são obrigatórios.");
        }
        if (sourceWeightG == null || sourceWeightG.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Peso de origem deve ser estritamente positivo.");
        }

        // 1. Determinação da Regra do Macro-Alvo (Estratégia Híbrida Determinística)
        MacroTarget activeTarget;
        if (targetMacroOverride != null) {
            activeTarget = targetMacroOverride;
        } else {
            DominantMacro dominant = sourceFood.getDominantMacro();
            if (dominant == DominantMacro.PROTEIN) {
                activeTarget = MacroTarget.PROTEIN;
            } else if (dominant == DominantMacro.CARB) {
                activeTarget = MacroTarget.CARB;
            } else if (dominant == DominantMacro.FAT) {
                activeTarget = MacroTarget.FAT;
            } else {
                // Origem BALANCED: Regra 2 (Desempate por Proteína >= 5.0g ou Isocalórico)
                if (sourceFood.getProteinG() != null && sourceFood.getProteinG().compareTo(FIVE) >= 0) {
                    activeTarget = MacroTarget.PROTEIN;
                } else {
                    activeTarget = MacroTarget.KCAL;
                }
            }
        }

        // 2. Cálculo do montante absoluto a igualar no Alimento de Origem
        BigDecimal targetConcentrationInDestination;
        BigDecimal absoluteAmountToMatch;

        switch (activeTarget) {
            case PROTEIN -> {
                absoluteAmountToMatch = scale100g(sourceFood.getProteinG(), sourceWeightG);
                targetConcentrationInDestination = targetFood.getProteinG() != null ? targetFood.getProteinG() : BigDecimal.ZERO;
                if (targetConcentrationInDestination.compareTo(MIN_MACRO_THRESHOLD) < 0) {
                    throw new IncompatibleFoodSubstitutionException(
                            String.format("Alimento '%s' possui concentração insuficiente de proteína (%sg/100g) para substituição equivalente.",
                                    targetFood.getName(), targetConcentrationInDestination)
                    );
                }
            }
            case CARB -> {
                absoluteAmountToMatch = scale100g(sourceFood.getCarbohydrateG(), sourceWeightG);
                targetConcentrationInDestination = targetFood.getCarbohydrateG() != null ? targetFood.getCarbohydrateG() : BigDecimal.ZERO;
                if (targetConcentrationInDestination.compareTo(MIN_MACRO_THRESHOLD) < 0) {
                    throw new IncompatibleFoodSubstitutionException(
                            String.format("Alimento '%s' possui concentração insuficiente de carboidrato (%sg/100g) para substituição equivalente.",
                                    targetFood.getName(), targetConcentrationInDestination)
                    );
                }
            }
            case FAT -> {
                absoluteAmountToMatch = scale100g(sourceFood.getLipidG(), sourceWeightG);
                targetConcentrationInDestination = targetFood.getLipidG() != null ? targetFood.getLipidG() : BigDecimal.ZERO;
                if (targetConcentrationInDestination.compareTo(MIN_MACRO_THRESHOLD) < 0) {
                    throw new IncompatibleFoodSubstitutionException(
                            String.format("Alimento '%s' possui concentração insuficiente de gordura (%sg/100g) para substituição equivalente.",
                                    targetFood.getName(), targetConcentrationInDestination)
                    );
                }
            }
            case KCAL -> {
                absoluteAmountToMatch = scale100g(sourceFood.getEnergyKcal(), sourceWeightG);
                targetConcentrationInDestination = targetFood.getEnergyKcal() != null ? targetFood.getEnergyKcal() : BigDecimal.ZERO;
                if (targetConcentrationInDestination.compareTo(MIN_KCAL_THRESHOLD) < 0) {
                    throw new IncompatibleFoodSubstitutionException(
                            String.format("Alimento '%s' possui densidade calórica insuficiente (%s kcal/100g) para substituição isocalórica.",
                                    targetFood.getName(), targetConcentrationInDestination)
                    );
                }
            }
            default -> throw new IllegalStateException("Macro-alvo não suportado: " + activeTarget);
        }

        // 3. Cálculo do Peso Necessário do Alimento B (W_B)
        BigDecimal calculatedWeightG = absoluteAmountToMatch
                .divide(targetConcentrationInDestination, 6, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED)
                .setScale(2, RoundingMode.HALF_UP);

        // 4. Sumário Nutricional das Porções
        FoodSummary sourceSummary = calculatePortionMacros(sourceFood, sourceWeightG);
        FoodSummary targetSummary = calculatePortionMacros(targetFood, calculatedWeightG);

        // 5. Cálculo dos Deltas e Detecção de Calorie Drift
        BigDecimal deltaKcal = targetSummary.energyKcal().subtract(sourceSummary.energyKcal()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal deltaProteinG = targetSummary.proteinG().subtract(sourceSummary.proteinG()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal deltaCarbohydrateG = targetSummary.carbohydrateG().subtract(sourceSummary.carbohydrateG()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal deltaLipidG = targetSummary.lipidG().subtract(sourceSummary.lipidG()).setScale(2, RoundingMode.HALF_UP);

        BigDecimal calorieDriftPercent = BigDecimal.ZERO;
        boolean calorieDriftWarning = false;
        String warningMessage = null;

        if (sourceSummary.energyKcal().compareTo(BigDecimal.ZERO) > 0) {
            calorieDriftPercent = deltaKcal.abs()
                    .divide(sourceSummary.energyKcal(), 4, RoundingMode.HALF_UP)
                    .multiply(ONE_HUNDRED)
                    .setScale(2, RoundingMode.HALF_UP);

            if (calorieDriftPercent.compareTo(DRIFT_PERCENT_THRESHOLD) > 0) {
                calorieDriftWarning = true;
                String sign = deltaKcal.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
                warningMessage = String.format(
                        "Atenção: A substituição mantém %s equivalente, porém varia as calorias em %s%s kcal (%s%s%%) devido à divergência nos macros secundários (P: %sg, C: %sg, G: %sg).",
                        activeTarget, sign, deltaKcal, sign, calorieDriftPercent, deltaProteinG, deltaCarbohydrateG, deltaLipidG
                );
            }
        }

        return new SubstitutionResult(
                sourceSummary,
                targetSummary,
                deltaKcal,
                deltaProteinG,
                deltaCarbohydrateG,
                deltaLipidG,
                calorieDriftPercent,
                calorieDriftWarning,
                warningMessage,
                activeTarget
        );
    }

    @Override
    public SubstitutionResult calculateSubstitution(UUID sourceFoodId, BigDecimal sourceWeightG, UUID targetFoodId, MacroTarget targetMacroOverride) {
        if (foodRepository == null) {
            throw new IllegalStateException("FoodRepository não injetado no NutritionMathService.");
        }
        Food sourceFood = foodRepository.findById(sourceFoodId)
                .orElseThrow(() -> new FoodNotFoundException("Alimento de origem não encontrado com ID: " + sourceFoodId));
        Food targetFood = foodRepository.findById(targetFoodId)
                .orElseThrow(() -> new FoodNotFoundException("Alimento alvo não encontrado com ID: " + targetFoodId));

        return calculateEquivalentSubstitution(sourceFood, sourceWeightG, targetFood, targetMacroOverride);
    }

    @Override
    public Food calculateProportionalNutrition(Food food, BigDecimal weightG) {
        if (food == null || weightG == null) {
            throw new IllegalArgumentException("Alimento e peso não podem ser nulos para cálculo proporcional.");
        }
        MicronutrientsData scaledMicros = calculatePortionMicronutrients(food.getMicronutrients(), weightG);

        return Food.builder()
                .id(food.getId())
                .name(food.getName())
                .source(food.getSource())
                .externalId(food.getExternalId())
                .category(food.getCategory())
                .dominantMacro(food.getDominantMacro())
                .servingSizeG(weightG.setScale(2, RoundingMode.HALF_UP))
                .energyKcal(scale100g(food.getEnergyKcal(), weightG))
                .proteinG(scale100g(food.getProteinG(), weightG))
                .carbohydrateG(scale100g(food.getCarbohydrateG(), weightG))
                .lipidG(scale100g(food.getLipidG(), weightG))
                .fiberG(scale100g(food.getFiberG(), weightG))
                .micronutrients(scaledMicros)
                .build();
    }

    @Override
    public DominantMacro calculateDominantMacro(BigDecimal proteinG, BigDecimal carbG, BigDecimal lipidG) {
        BigDecimal p = proteinG != null ? proteinG : BigDecimal.ZERO;
        BigDecimal c = carbG != null ? carbG : BigDecimal.ZERO;
        BigDecimal l = lipidG != null ? lipidG : BigDecimal.ZERO;

        BigDecimal pKcal = p.multiply(FOUR);
        BigDecimal cKcal = c.multiply(FOUR);
        BigDecimal lKcal = l.multiply(NINE);
        BigDecimal totalKcal = pKcal.add(cKcal).add(lKcal);

        if (totalKcal.compareTo(BigDecimal.ZERO) <= 0) {
            return DominantMacro.BALANCED;
        }

        BigDecimal pRatio = pKcal.divide(totalKcal, 4, RoundingMode.HALF_UP);
        BigDecimal cRatio = cKcal.divide(totalKcal, 4, RoundingMode.HALF_UP);
        BigDecimal lRatio = lKcal.divide(totalKcal, 4, RoundingMode.HALF_UP);

        BigDecimal threshold = new BigDecimal("0.50");
        if (pRatio.compareTo(threshold) >= 0) return DominantMacro.PROTEIN;
        if (cRatio.compareTo(threshold) >= 0) return DominantMacro.CARB;
        if (lRatio.compareTo(threshold) >= 0) return DominantMacro.FAT;

        if (pKcal.compareTo(cKcal) >= 0 && pKcal.compareTo(lKcal) >= 0) {
            return DominantMacro.PROTEIN;
        } else if (cKcal.compareTo(pKcal) >= 0 && cKcal.compareTo(lKcal) >= 0) {
            return DominantMacro.CARB;
        } else {
            return DominantMacro.FAT;
        }
    }

    @Override
    public List<QuickSubstitutionDto> getQuickSubstitutions(
            UUID foodId,
            BigDecimal weightG,
            MacroTarget targetMacroOverride,
            int limit
    ) {
        if (foodRepository == null) {
            throw new IllegalStateException("FoodRepository não injetado no NutritionMathService.");
        }
        Food sourceFood = foodRepository.findById(foodId)
                .orElseThrow(() -> new FoodNotFoundException("Alimento de origem não encontrado: " + foodId));

        BigDecimal effectiveWeight = (weightG != null && weightG.compareTo(BigDecimal.ZERO) > 0)
                ? weightG
                : ONE_HUNDRED;

        int effectiveLimit = (limit > 0) ? Math.min(limit, 20) : 5;

        // Busca candidatos no mesmo grupo culinário ou no mesmo macro dominante
        Set<UUID> seenIds = new HashSet<>();
        List<Food> candidates = new ArrayList<>();

        if (sourceFood.getFoodGroup() != null && !sourceFood.getFoodGroup().isBlank()) {
            List<Food> groupCandidates = foodRepository.findCandidatesByFoodGroup(
                    sourceFood.getFoodGroup(),
                    sourceFood.getId(),
                    PageRequest.of(0, 30)
            );
            for (Food f : groupCandidates) {
                if (seenIds.add(f.getId())) {
                    candidates.add(f);
                }
            }
        }

        if (candidates.size() < 10) {
            List<Food> macroCandidates = foodRepository.findCandidatesByDominantMacro(
                    sourceFood.getDominantMacro(),
                    sourceFood.getId(),
                    PageRequest.of(0, 30)
            );
            for (Food f : macroCandidates) {
                if (seenIds.add(f.getId())) {
                    candidates.add(f);
                }
            }
        }

        List<QuickSubstitutionDto> substitutions = new ArrayList<>();

        for (Food targetFood : candidates) {
            try {
                SubstitutionResult result = calculateEquivalentSubstitution(
                        sourceFood,
                        effectiveWeight,
                        targetFood,
                        targetMacroOverride
                );

                List<FoodPortionDto> portions = targetFood.getPortions() != null
                        ? targetFood.getPortions().stream()
                        .map(p -> new FoodPortionDto(p.getId(), p.getPortionName(), p.getWeightG()))
                        .toList()
                        : Collections.emptyList();

                substitutions.add(new QuickSubstitutionDto(
                        targetFood.getId(),
                        targetFood.getName(),
                        targetFood.getFoodGroup(),
                        result.targetFood().weightG(),
                        result.targetFood().energyKcal(),
                        result.targetFood().proteinG(),
                        result.targetFood().carbohydrateG(),
                        result.targetFood().lipidG(),
                        result.deltaKcal(),
                        result.deltaProteinG(),
                        result.deltaCarbohydrateG(),
                        result.deltaLipidG(),
                        result.calorieDriftPercent(),
                        result.calorieDriftWarning(),
                        result.warningMessage(),
                        portions
                ));
            } catch (IncompatibleFoodSubstitutionException | IllegalArgumentException ignored) {
                // Candidato biologicamente incompatível para substituição equivalente
            }
        }

        // Ordena por menor módulo de desvio calórico (|Δkcal| ASC)
        substitutions.sort(Comparator.comparing(dto -> dto.deltaKcal().abs()));

        return substitutions.stream()
                .limit(effectiveLimit)
                .toList();
    }
}
