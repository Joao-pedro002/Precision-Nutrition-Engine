package com.precisionnutrition.service;

import com.precisionnutrition.model.dto.*;
import com.precisionnutrition.model.entity.*;
import com.precisionnutrition.util.AppConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardServiceImpl implements DashboardService {

    private final DailyTargetService dailyTargetService;
    private final DailyLogService dailyLogService;
    private final MealService mealService;
    private final NutritionMathService nutritionMathService;

    @Override
    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(UUID userId, LocalDate date) {
        UUID resolvedUserId = userId != null ? userId : AppConstants.DEFAULT_USER_ID;
        LocalDate resolvedDate = date != null ? date : LocalDate.now();

        DailyTarget activeTarget = dailyTargetService.getActiveTarget(resolvedUserId);
        DailyLog dailyLog = dailyLogService.getOrCreateDailyLog(resolvedUserId, resolvedDate);

        // 1. Resumo Diário (Macronutrientes, Zonas Calóricas e Hidratação)
        DailySummaryDto summary = buildDailySummary(dailyLog, activeTarget);

        // 2. Matriz de Micronutrientes (9 chaves + razão Na:K)
        MicronutrientMatrixDto micronutrients = buildMicronutrientMatrix(dailyLog, activeTarget);

        // 3. Refeições Prescritas com Cálculos Proporcionais e Status de Conclusão
        List<Meal> meals = mealService.getUserMeals(resolvedUserId);
        List<MealDashboardDto> mealDtos = buildMealDashboardList(meals, dailyLog);

        // 4. Entradas Registradas (Logs)
        List<DailyLogEntryDto> loggedEntries = dailyLog.getEntries().stream()
                .map(this::mapToEntryDto)
                .sorted(Comparator.comparing(DailyLogEntryDto::loggedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        return new DashboardResponse(
                resolvedDate,
                activeTarget,
                summary,
                micronutrients,
                mealDtos,
                loggedEntries
        );
    }

    private DailySummaryDto buildDailySummary(DailyLog logEntity, DailyTarget target) {
        BigDecimal totalKcal = BigDecimal.ZERO;
        BigDecimal totalP = BigDecimal.ZERO;
        BigDecimal totalC = BigDecimal.ZERO;
        BigDecimal totalG = BigDecimal.ZERO;
        BigDecimal totalF = BigDecimal.ZERO;

        if (logEntity != null && logEntity.getEntries() != null) {
            for (DailyLogEntry e : logEntity.getEntries()) {
                if (e == null) {
                    continue;
                }
                BigDecimal kcal = e.isOffDiet()
                        ? (e.getCustomKcal() != null ? e.getCustomKcal() : BigDecimal.ZERO)
                        : (e.getConsumedEnergyKcal() != null ? e.getConsumedEnergyKcal() : BigDecimal.ZERO);
                totalKcal = totalKcal.add(kcal);
                totalP = totalP.add(e.getConsumedProteinG() != null ? e.getConsumedProteinG() : BigDecimal.ZERO);
                totalC = totalC.add(e.getConsumedCarbohydrateG() != null ? e.getConsumedCarbohydrateG() : BigDecimal.ZERO);
                totalG = totalG.add(e.getConsumedLipidG() != null ? e.getConsumedLipidG() : BigDecimal.ZERO);
                totalF = totalF.add(e.getConsumedFiberG() != null ? e.getConsumedFiberG() : BigDecimal.ZERO);
            }
        }

        totalKcal = totalKcal.setScale(2, RoundingMode.HALF_UP);
        totalP = totalP.setScale(2, RoundingMode.HALF_UP);
        totalC = totalC.setScale(2, RoundingMode.HALF_UP);
        totalG = totalG.setScale(2, RoundingMode.HALF_UP);
        totalF = totalF.setScale(2, RoundingMode.HALF_UP);

        BigDecimal targetKcalMin = target != null && target.getTargetKcalMin() != null ? target.getTargetKcalMin() : BigDecimal.valueOf(2000);
        BigDecimal targetKcalMax = target != null && target.getTargetKcalMax() != null ? target.getTargetKcalMax() : BigDecimal.valueOf(2103);
        BigDecimal maintenanceCeiling = target != null && target.getMaintenanceKcalCeiling() != null ? target.getMaintenanceKcalCeiling() : BigDecimal.valueOf(2500);

        CaloricZoneResult zone = nutritionMathService.evaluateCaloricZone(totalKcal, targetKcalMin, targetKcalMax, maintenanceCeiling);

        BigDecimal targetP = target != null && target.getTargetProteinG() != null ? target.getTargetProteinG() : BigDecimal.valueOf(160);
        BigDecimal targetC = target != null && target.getTargetCarbohydrateG() != null ? target.getTargetCarbohydrateG() : BigDecimal.valueOf(220);
        BigDecimal targetG = target != null && target.getTargetLipidG() != null ? target.getTargetLipidG() : BigDecimal.valueOf(60);
        BigDecimal targetF = target != null && target.getTargetFiberG() != null ? target.getTargetFiberG() : BigDecimal.valueOf(30);

        BigDecimal pPct = targetP.compareTo(BigDecimal.ZERO) > 0 ? totalP.multiply(BigDecimal.valueOf(100)).divide(targetP, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal cPct = targetC.compareTo(BigDecimal.ZERO) > 0 ? totalC.multiply(BigDecimal.valueOf(100)).divide(targetC, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal gPct = targetG.compareTo(BigDecimal.ZERO) > 0 ? totalG.multiply(BigDecimal.valueOf(100)).divide(targetG, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        BigDecimal waterConsumed = logEntity != null && logEntity.getWaterConsumedMl() != null ? logEntity.getWaterConsumedMl() : BigDecimal.ZERO;
        BigDecimal targetWater = target != null && target.getTargetWaterMl() != null ? target.getTargetWaterMl() : BigDecimal.valueOf(3500);
        BigDecimal waterPct = targetWater.compareTo(BigDecimal.ZERO) > 0 ? waterConsumed.multiply(BigDecimal.valueOf(100)).divide(targetWater, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        return new DailySummaryDto(
                totalKcal,
                targetKcalMin,
                targetKcalMax,
                maintenanceCeiling,
                zone,
                totalP,
                targetP,
                pPct,
                totalC,
                targetC,
                cPct,
                totalG,
                targetG,
                gPct,
                totalF,
                targetF,
                waterConsumed,
                targetWater,
                waterPct
        );
    }

    private MicronutrientMatrixDto buildMicronutrientMatrix(DailyLog logEntity, DailyTarget target) {
        BigDecimal sumCalcium = BigDecimal.ZERO;
        BigDecimal sumIron = BigDecimal.ZERO;
        BigDecimal sumMagnesium = BigDecimal.ZERO;
        BigDecimal sumPotassium = BigDecimal.ZERO;
        BigDecimal sumSodium = BigDecimal.ZERO;
        BigDecimal sumZinc = BigDecimal.ZERO;
        BigDecimal sumVitaminC = BigDecimal.ZERO;
        BigDecimal sumVitaminB12 = BigDecimal.ZERO;
        BigDecimal sumCholesterol = BigDecimal.ZERO;

        if (logEntity != null && logEntity.getEntries() != null) {
            for (DailyLogEntry entry : logEntity.getEntries()) {
                MicronutrientsData m = entry.getConsumedMicronutrients();
                if (m != null) {
                    sumCalcium = sumCalcium.add(m.safeCalcium());
                    sumIron = sumIron.add(m.safeIron());
                    sumMagnesium = sumMagnesium.add(m.safeMagnesium());
                    sumPotassium = sumPotassium.add(m.safePotassium());
                    sumSodium = sumSodium.add(m.safeSodium());
                    sumZinc = sumZinc.add(m.safeZinc());
                    sumVitaminC = sumVitaminC.add(m.safeVitaminC());
                    sumVitaminB12 = sumVitaminB12.add(m.safeVitaminB12());
                    sumCholesterol = sumCholesterol.add(m.safeCholesterol());
                }
            }
        }

        MicronutrientsData targetM = (target != null && target.getTargetMicronutrients() != null)
                ? target.getTargetMicronutrients()
                : MicronutrientsData.empty();

        BigDecimal naKRatio = nutritionMathService.calculateSodiumPotassiumRatio(sumSodium, sumPotassium);
        String naKStatus;
        if (sumPotassium.compareTo(BigDecimal.ZERO) == 0) {
            naKStatus = "Indeterminado (Sem ingestão registrada de potássio)";
        } else if (naKRatio.compareTo(new BigDecimal("0.60")) <= 0) {
            naKStatus = "Excelente (Protetor cardiovascular ótimo, Na:K <= 0.60)";
        } else if (naKRatio.compareTo(BigDecimal.ONE) <= 0) {
            naKStatus = "Adequado (Na:K <= 1.00)";
        } else {
            naKStatus = "Atenção (Consumo excessivo de sódio em relação ao potássio)";
        }

        List<MicronutrientComparisonDto> comparisons = List.of(
                createComparison("calcium_mg", "Cálcio", "mg", sumCalcium, targetM.safeCalcium()),
                createComparison("iron_mg", "Ferro", "mg", sumIron, targetM.safeIron()),
                createComparison("magnesium_mg", "Magnésio", "mg", sumMagnesium, targetM.safeMagnesium()),
                createComparison("potassium_mg", "Potássio", "mg", sumPotassium, targetM.safePotassium()),
                createComparison("sodium_mg", "Sódio", "mg", sumSodium, targetM.safeSodium()),
                createComparison("zinc_mg", "Zinco", "mg", sumZinc, targetM.safeZinc()),
                createComparison("vitamin_c_mg", "Vitamina C", "mg", sumVitaminC, targetM.safeVitaminC()),
                createComparison("vitamin_b12_mcg", "Vitamina B12", "mcg", sumVitaminB12, targetM.safeVitaminB12()),
                createComparison("cholesterol_mg", "Colesterol", "mg", sumCholesterol, targetM.safeCholesterol())
        );

        return new MicronutrientMatrixDto(naKRatio, naKStatus, comparisons);
    }

    private MicronutrientComparisonDto createComparison(String key, String displayName, String unit, BigDecimal consumed, BigDecimal target) {
        BigDecimal safeConsumed = consumed != null ? consumed.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal safeTarget = target != null ? target.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal percentage = (safeTarget.compareTo(BigDecimal.ZERO) > 0)
                ? safeConsumed.multiply(BigDecimal.valueOf(100)).divide(safeTarget, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new MicronutrientComparisonDto(key, displayName, unit, safeConsumed, safeTarget, percentage);
    }

    private List<MealDashboardDto> buildMealDashboardList(List<Meal> meals, DailyLog dailyLog) {
        List<MealDashboardDto> result = new ArrayList<>();

        for (Meal meal : meals) {
            // Group items by optionNumber
            Map<Integer, List<MealItem>> optionGroups = (meal.getItems() != null)
                    ? meal.getItems().stream().collect(Collectors.groupingBy(MealItem::getOptionNumber))
                    : Collections.emptyMap();

            List<MealOptionDto> optionDtos = new ArrayList<>();
            for (Map.Entry<Integer, List<MealItem>> entry : optionGroups.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()).toList()) {

                Integer optNum = entry.getKey();
                List<MealItem> items = entry.getValue();

                BigDecimal optKcal = BigDecimal.ZERO;
                BigDecimal optP = BigDecimal.ZERO;
                BigDecimal optC = BigDecimal.ZERO;
                BigDecimal optG = BigDecimal.ZERO;
                BigDecimal optF = BigDecimal.ZERO;

                List<MealItemCalculatedDto> itemCalculatedDtos = new ArrayList<>();
                for (MealItem item : items) {
                    Food proportional = nutritionMathService.calculateProportionalNutrition(item.getFood(), item.getWeightG());

                    optKcal = optKcal.add(proportional.getEnergyKcal());
                    optP = optP.add(proportional.getProteinG());
                    optC = optC.add(proportional.getCarbohydrateG());
                    optG = optG.add(proportional.getLipidG());
                    optF = optF.add(proportional.getFiberG());

                    itemCalculatedDtos.add(new MealItemCalculatedDto(
                            item.getId(),
                            item.getFood().getId(),
                            item.getFood().getName(),
                            item.getWeightG(),
                            proportional.getEnergyKcal(),
                            proportional.getProteinG(),
                            proportional.getCarbohydrateG(),
                            proportional.getLipidG(),
                            proportional.getFiberG(),
                            proportional.getMicronutrients()
                    ));
                }

                optionDtos.add(new MealOptionDto(
                        optNum,
                        optKcal.setScale(2, RoundingMode.HALF_UP),
                        optP.setScale(2, RoundingMode.HALF_UP),
                        optC.setScale(2, RoundingMode.HALF_UP),
                        optG.setScale(2, RoundingMode.HALF_UP),
                        optF.setScale(2, RoundingMode.HALF_UP),
                        itemCalculatedDtos
                ));
            }

            // Determine if completed and which option was completed
            List<DailyLogEntry> mealEntries = (dailyLog != null && dailyLog.getEntries() != null)
                    ? dailyLog.getEntries().stream()
                        .filter(e -> e.getMeal() != null && e.getMeal().getId().equals(meal.getId()) && !e.isOffDiet())
                        .toList()
                    : Collections.emptyList();

            boolean isCompleted = !mealEntries.isEmpty();
            Integer completedOptionNumber = null;

            if (isCompleted) {
                Set<UUID> loggedFoodIds = mealEntries.stream().map(e -> e.getFood().getId()).collect(Collectors.toSet());
                for (Map.Entry<Integer, List<MealItem>> entry : optionGroups.entrySet()) {
                    Set<UUID> optionFoodIds = entry.getValue().stream().map(i -> i.getFood().getId()).collect(Collectors.toSet());
                    if (loggedFoodIds.equals(optionFoodIds) && mealEntries.size() == entry.getValue().size()) {
                        completedOptionNumber = entry.getKey();
                        break;
                    }
                }
                if (completedOptionNumber == null) {
                    completedOptionNumber = 1; // Fallback se editado
                }
            }

            result.add(new MealDashboardDto(
                    meal.getId(),
                    meal.getName(),
                    meal.getOrderIndex(),
                    meal.getTargetTime(),
                    isCompleted,
                    completedOptionNumber,
                    optionDtos
            ));
        }

        return result;
    }

    private DailyLogEntryDto mapToEntryDto(DailyLogEntry entry) {
        return new DailyLogEntryDto(
                entry.getId(),
                entry.getMeal() != null ? entry.getMeal().getId() : null,
                entry.getFood() != null ? entry.getFood().getId() : null,
                entry.getFoodNameSnapshot(),
                entry.getWeightConsumedG(),
                entry.getConsumedEnergyKcal(),
                entry.getConsumedProteinG(),
                entry.getConsumedCarbohydrateG(),
                entry.getConsumedLipidG(),
                entry.getConsumedFiberG(),
                entry.getConsumedMicronutrients(),
                entry.isOffDiet(),
                entry.getLoggedAt()
        );
    }
}
