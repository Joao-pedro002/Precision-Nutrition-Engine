package com.precisionnutrition.service;

import com.precisionnutrition.exception.FoodNotFoundException;
import com.precisionnutrition.model.dto.*;
import com.precisionnutrition.model.entity.*;
import com.precisionnutrition.model.enums.FoodSource;
import com.precisionnutrition.repository.DailyLogEntryRepository;
import com.precisionnutrition.repository.DailyLogRepository;
import com.precisionnutrition.repository.FoodRepository;
import com.precisionnutrition.repository.MealRepository;
import com.precisionnutrition.security.AuthenticatedUser;
import com.precisionnutrition.util.AppConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
public class DailyLogServiceImpl implements DailyLogService {

    private final DailyLogRepository dailyLogRepository;
    private final DailyLogEntryRepository dailyLogEntryRepository;
    private final DailyTargetService dailyTargetService;
    private final MealRepository mealRepository;
    private final FoodRepository foodRepository;
    private final NutritionMathService nutritionMathService;

    @Override
    @Transactional(readOnly = true)
    public Optional<DailyLog> findDailyLog(UUID userId, LocalDate date) {
        UUID resolvedUserId = userId != null ? userId : AppConstants.DEFAULT_USER_ID;
        return dailyLogRepository.findByUserIdAndDate(resolvedUserId, date);
    }

    @Override
    @Transactional
    public DailyLog getOrCreateDailyLog(UUID userId, LocalDate date) {
        UUID resolvedUserId = userId != null ? userId : AppConstants.DEFAULT_USER_ID;
        return dailyLogRepository.findByUserIdAndDate(resolvedUserId, date)
                .orElseGet(() -> {
                    DailyLog newLog = DailyLog.builder()
                            .userId(resolvedUserId)
                            .date(date)
                            .waterConsumedMl(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                            .entries(new ArrayList<>())
                            .build();
                    return dailyLogRepository.save(newLog);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public DailyLogResponse getDailyLogResponse(UUID userId, LocalDate date) {
        UUID resolvedUserId = userId != null ? userId : AppConstants.DEFAULT_USER_ID;
        DailyLog logEntity = getOrCreateDailyLog(resolvedUserId, date);
        DailyTarget target = dailyTargetService.getActiveTarget(resolvedUserId);

        DailySummaryDto summary = buildDailySummary(logEntity, target);

        List<DailyLogEntryDto> entryDtos = logEntity.getEntries().stream()
                .map(this::mapToEntryDto)
                .sorted(Comparator.comparing(DailyLogEntryDto::loggedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        BigDecimal waterTarget = target != null && target.getTargetWaterMl() != null
                ? target.getTargetWaterMl()
                : BigDecimal.valueOf(3500);

        BigDecimal waterPercent = waterTarget.compareTo(BigDecimal.ZERO) > 0
                ? logEntity.getWaterConsumedMl().multiply(BigDecimal.valueOf(100)).divide(waterTarget, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new DailyLogResponse(
                logEntity.getId(),
                logEntity.getUserId(),
                logEntity.getDate(),
                logEntity.getWaterConsumedMl(),
                waterTarget,
                waterPercent,
                summary,
                entryDtos
        );
    }

    @Override
    @Transactional
    public WaterUpdateResponse updateWater(LocalDate date, WaterUpdateRequest request) {
        UUID userId = request.userId() != null ? request.userId() : AppConstants.DEFAULT_USER_ID;
        DailyLog dailyLog = getOrCreateDailyLog(userId, date);

        BigDecimal current = dailyLog.getWaterConsumedMl() != null ? dailyLog.getWaterConsumedMl() : BigDecimal.ZERO;
        BigDecimal newAmount;

        if ("INCREMENT".equalsIgnoreCase(request.mode())) {
            newAmount = current.add(request.amountMl());
        } else if ("SET".equalsIgnoreCase(request.mode())) {
            newAmount = request.amountMl();
        } else {
            throw new IllegalArgumentException("Modo inválido: use INCREMENT ou SET");
        }

        if (newAmount.compareTo(BigDecimal.ZERO) < 0) {
            newAmount = BigDecimal.ZERO;
        }
        newAmount = newAmount.setScale(2, RoundingMode.HALF_UP);
        dailyLog.setWaterConsumedMl(newAmount);
        dailyLogRepository.save(dailyLog);

        DailyTarget target = dailyTargetService.getActiveTarget(userId);
        BigDecimal waterTarget = target != null && target.getTargetWaterMl() != null
                ? target.getTargetWaterMl()
                : BigDecimal.valueOf(3500);

        BigDecimal waterPercent = waterTarget.compareTo(BigDecimal.ZERO) > 0
                ? newAmount.multiply(BigDecimal.valueOf(100)).divide(waterTarget, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new WaterUpdateResponse(date, newAmount, waterTarget, waterPercent);
    }

    @Override
    @Transactional
    public DailyLog checkMealOption(LocalDate date, CheckMealRequest request) {
        UUID userId = request.userId() != null ? request.userId() : AppConstants.DEFAULT_USER_ID;
        DailyLog dailyLog = getOrCreateDailyLog(userId, date);

        Meal meal = mealRepository.findById(request.mealId())
                .orElseThrow(() -> new IllegalArgumentException("Refeição não encontrada: " + request.mealId()));
        if (meal.getUserId() != null && !meal.getUserId().equals(userId)) {
            throw new AccessDeniedException("Você não tem permissão para marcar esta refeição.");
        }

        List<DailyLogEntry> existingEntries = dailyLog.getEntries().stream()
                .filter(e -> e.getMeal() != null && e.getMeal().getId().equals(meal.getId()))
                .toList();

        boolean isSameOptionAlreadyLogged = false;
        if (!existingEntries.isEmpty()) {
            List<MealItem> targetOptionItems = meal.getItems().stream()
                    .filter(item -> item.getOptionNumber().equals(request.optionNumber()))
                    .toList();

            Set<UUID> targetFoodIds = targetOptionItems.stream().map(i -> i.getFood().getId()).collect(Collectors.toSet());
            Set<UUID> loggedFoodIds = existingEntries.stream().map(e -> e.getFood().getId()).collect(Collectors.toSet());

            if (targetFoodIds.equals(loggedFoodIds) && existingEntries.size() == targetOptionItems.size()) {
                isSameOptionAlreadyLogged = true;
            }
        }

        if (isSameOptionAlreadyLogged) {
            // Desmarcar (toggle off)
            dailyLog.getEntries().removeAll(existingEntries);
            dailyLogEntryRepository.deleteAll(existingEntries);
            log.info("Desmarcada refeição {} para data {}", meal.getName(), date);
        } else {
            // Se existiam entradas de outra opção para esta mesma refeição, limpa antes
            if (!existingEntries.isEmpty()) {
                dailyLog.getEntries().removeAll(existingEntries);
                dailyLogEntryRepository.deleteAll(existingEntries);
            }

            List<MealItem> itemsToAdd = meal.getItems().stream()
                    .filter(item -> item.getOptionNumber().equals(request.optionNumber()))
                    .toList();

            for (MealItem item : itemsToAdd) {
                Food food = item.getFood();
                BigDecimal weight = item.getWeightG();
                Food proportional = nutritionMathService.calculateProportionalNutrition(food, weight);

                DailyLogEntry entry = DailyLogEntry.builder()
                        .dailyLog(dailyLog)
                        .meal(meal)
                        .food(food)
                        .foodNameSnapshot(food.getName())
                        .weightConsumedG(weight.setScale(2, RoundingMode.HALF_UP))
                        .consumedEnergyKcal(proportional.getEnergyKcal())
                        .consumedProteinG(proportional.getProteinG())
                        .consumedCarbohydrateG(proportional.getCarbohydrateG())
                        .consumedLipidG(proportional.getLipidG())
                        .consumedFiberG(proportional.getFiberG())
                        .consumedMicronutrients(proportional.getMicronutrients() != null ? proportional.getMicronutrients() : MicronutrientsData.empty())
                        .isOffDiet(false)
                        .build();

                dailyLog.getEntries().add(entry);
            }
            log.info("Marcada opção {} da refeição {} para data {}", request.optionNumber(), meal.getName(), date);
        }

        DailyLog savedLog = dailyLogRepository.save(dailyLog);

        // Forçar a hidratação dos Proxies JPA para o Jackson não quebrar
        Hibernate.initialize(savedLog.getEntries());
        if (savedLog.getEntries() != null) {
            for (var entry : savedLog.getEntries()) {
                if (entry.getMeal() != null) {
                    Hibernate.initialize(entry.getMeal());
                }
                if (entry.getFood() != null) {
                    Hibernate.initialize(entry.getFood());
                }
            }
        }

        return savedLog;
    }

    @Override
    @Transactional
    public DailyLog uncheckMealOption(LocalDate date, CheckMealRequest request) {
        UUID userId = request.userId() != null ? request.userId() : AppConstants.DEFAULT_USER_ID;
        DailyLog dailyLog = getOrCreateDailyLog(userId, date);

        List<DailyLogEntry> existingEntries = dailyLog.getEntries().stream()
                .filter(e -> e.getMeal() != null && e.getMeal().getId().equals(request.mealId()))
                .toList();

        if (!existingEntries.isEmpty()) {
            dailyLog.getEntries().removeAll(existingEntries);
            dailyLogEntryRepository.deleteAll(existingEntries);
            log.info("Desmarcada refeição {} para data {}", request.mealId(), date);
        }

        DailyLog savedLog = dailyLogRepository.save(dailyLog);

        // Forçar a hidratação dos Proxies JPA para o Jackson não quebrar
        Hibernate.initialize(savedLog.getEntries());
        if (savedLog.getEntries() != null) {
            for (var entry : savedLog.getEntries()) {
                if (entry.getMeal() != null) {
                    Hibernate.initialize(entry.getMeal());
                }
                if (entry.getFood() != null) {
                    Hibernate.initialize(entry.getFood());
                }
            }
        }

        return savedLog;
    }

    @Override
    @Transactional
    public DailyLogEntry addOffDietEntry(LocalDate date, OffDietEntryRequest request) {
        UUID userId = request.userId() != null ? request.userId() : AppConstants.DEFAULT_USER_ID;
        DailyLog dailyLog = getOrCreateDailyLog(userId, date);

        Food food;
        BigDecimal weight = request.weightConsumedG() != null ? request.weightConsumedG() : BigDecimal.valueOf(100);
        BigDecimal energy, protein, carb, lipid, fiber;
        MicronutrientsData micros;

        if (request.foodId() != null) {
            food = foodRepository.findById(request.foodId())
                    .orElseThrow(() -> new FoodNotFoundException("Alimento não encontrado com ID: " + request.foodId()));
            Food prop = nutritionMathService.calculateProportionalNutrition(food, weight);
            energy = request.energyKcal() != null ? request.energyKcal() : (prop.getEnergyKcal() != null ? prop.getEnergyKcal() : BigDecimal.ZERO);
            protein = request.proteinG() != null ? request.proteinG() : (prop.getProteinG() != null ? prop.getProteinG() : BigDecimal.ZERO);
            carb = request.carbohydrateG() != null ? request.carbohydrateG() : (prop.getCarbohydrateG() != null ? prop.getCarbohydrateG() : BigDecimal.ZERO);
            lipid = request.lipidG() != null ? request.lipidG() : (prop.getLipidG() != null ? prop.getLipidG() : BigDecimal.ZERO);
            fiber = request.fiberG() != null ? request.fiberG() : (prop.getFiberG() != null ? prop.getFiberG() : BigDecimal.ZERO);
            micros = prop.getMicronutrients();
        } else {
            String name = request.customName() != null && !request.customName().isBlank()
                    ? request.customName()
                    : "Refeição Livre / Fora da Dieta";
            energy = request.energyKcal() != null ? request.energyKcal() : BigDecimal.ZERO;
            protein = request.proteinG() != null ? request.proteinG() : BigDecimal.ZERO;
            carb = request.carbohydrateG() != null ? request.carbohydrateG() : BigDecimal.ZERO;
            lipid = request.lipidG() != null ? request.lipidG() : BigDecimal.ZERO;
            fiber = request.fiberG() != null ? request.fiberG() : BigDecimal.ZERO;
            micros = MicronutrientsData.empty();

            food = Food.builder()
                    .name(name)
                    .source(FoodSource.CUSTOM)
                    .category("Custom")
                    .servingSizeG(weight)
                    .energyKcal(energy)
                    .proteinG(protein)
                    .carbohydrateG(carb)
                    .lipidG(lipid)
                    .fiberG(fiber)
                    .dominantMacro(nutritionMathService.calculateDominantMacro(protein, carb, lipid))
                    .micronutrients(micros)
                    .build();
            food = foodRepository.save(food);
        }

        Meal meal = (request.mealId() != null)
                ? mealRepository.findById(request.mealId()).orElse(null)
                : null;

        if (meal != null) {
            List<DailyLogEntry> existingForMeal = dailyLog.getEntries().stream()
                    .filter(e -> e.getMeal() != null && e.getMeal().getId().equals(meal.getId()))
                    .toList();
            if (!existingForMeal.isEmpty()) {
                dailyLog.getEntries().removeAll(existingForMeal);
                dailyLogEntryRepository.deleteAll(existingForMeal);
            }
        }

        DailyLogEntry entry = DailyLogEntry.builder()
                .dailyLog(dailyLog)
                .meal(meal)
                .food(food)
                .foodNameSnapshot(food.getName())
                .weightConsumedG(weight.setScale(2, RoundingMode.HALF_UP))
                .consumedEnergyKcal(energy.setScale(2, RoundingMode.HALF_UP))
                .consumedProteinG(protein.setScale(2, RoundingMode.HALF_UP))
                .consumedCarbohydrateG(carb.setScale(2, RoundingMode.HALF_UP))
                .consumedLipidG(lipid.setScale(2, RoundingMode.HALF_UP))
                .consumedFiberG(fiber.setScale(2, RoundingMode.HALF_UP))
                .consumedMicronutrients(micros != null ? micros : MicronutrientsData.empty())
                .isOffDiet(true)
                .build();

        DailyLogEntry savedEntry = dailyLogEntryRepository.save(entry);
        dailyLog.getEntries().add(savedEntry);
        dailyLogRepository.saveAndFlush(dailyLog);
        log.info("Lançamento fora da dieta adicionado para data {}: {}", date, food.getName());

        // Forçar a hidratação dos Proxies JPA para o Jackson não quebrar
        if (savedEntry.getMeal() != null) {
            Hibernate.initialize(savedEntry.getMeal());
        }
        if (savedEntry.getFood() != null) {
            Hibernate.initialize(savedEntry.getFood());
        }
        Hibernate.initialize(dailyLog.getEntries());
        if (dailyLog.getEntries() != null) {
            for (var e : dailyLog.getEntries()) {
                if (e.getMeal() != null) {
                    Hibernate.initialize(e.getMeal());
                }
                if (e.getFood() != null) {
                    Hibernate.initialize(e.getFood());
                }
            }
        }

        return savedEntry;
    }

    @Override
    @Transactional
    public void removeEntry(UUID entryId) {
        DailyLogEntry entry = dailyLogEntryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Entrada de log não encontrada com ID: " + entryId));
        DailyLog logEntity = entry.getDailyLog();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AuthenticatedUser user) {
            if (logEntity != null && logEntity.getUserId() != null && !logEntity.getUserId().equals(user.id())) {
                throw new AccessDeniedException("Você não tem permissão para remover esta entrada.");
            }
        }
        if (logEntity != null) {
            logEntity.getEntries().remove(entry);
        }
        dailyLogEntryRepository.delete(entry);
        log.info("Entrada {} removida do diário", entryId);
    }

    public DailySummaryDto buildDailySummary(DailyLog logEntity, DailyTarget target) {
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
