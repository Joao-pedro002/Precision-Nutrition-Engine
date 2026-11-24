package com.precisionnutrition.service;

import com.precisionnutrition.model.dto.DailyTargetRequest;
import com.precisionnutrition.model.dto.MicronutrientsData;
import com.precisionnutrition.model.entity.DailyTarget;
import com.precisionnutrition.repository.DailyTargetRepository;
import com.precisionnutrition.util.AppConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class DailyTargetServiceImpl implements DailyTargetService {

    private final DailyTargetRepository dailyTargetRepository;
    private final MealService mealService;

    @Autowired
    public DailyTargetServiceImpl(DailyTargetRepository dailyTargetRepository, MealService mealService) {
        this.dailyTargetRepository = dailyTargetRepository;
        this.mealService = mealService;
    }

    public DailyTargetServiceImpl(DailyTargetRepository dailyTargetRepository) {
        this(dailyTargetRepository, null);
    }

    @Override
    @Transactional(readOnly = true)
    public DailyTarget getActiveTarget(UUID userId) {
        UUID effectiveUser = (userId != null) ? userId : AppConstants.DEFAULT_USER_ID;
        return dailyTargetRepository.findByUserIdAndIsActiveTrue(effectiveUser)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailyTarget> getUserTargets(UUID userId) {
        UUID effectiveUser = (userId != null) ? userId : AppConstants.DEFAULT_USER_ID;
        return dailyTargetRepository.findByUserIdOrderByCreatedAtDesc(effectiveUser);
    }

    @Override
    @Transactional
    public DailyTarget createTarget(DailyTargetRequest request) {
        UUID effectiveUser = (request.userId() != null) ? request.userId() : AppConstants.DEFAULT_USER_ID;

        boolean willBeActive = Boolean.TRUE.equals(request.isActive());

        if (willBeActive) {
            // Desativa qualquer protocolo ativo anterior (respeitando o índice parcial único) com flush imediato
            dailyTargetRepository.findByUserIdAndIsActiveTrue(effectiveUser).ifPresent(target -> {
                target.setActive(false);
                dailyTargetRepository.saveAndFlush(target);
            });
        }

        DailyTarget target = DailyTarget.builder()
                .userId(effectiveUser)
                .isActive(willBeActive)
                .targetKcalMin(request.targetKcalMin().setScale(2, RoundingMode.HALF_UP))
                .targetKcalMax(request.targetKcalMax().setScale(2, RoundingMode.HALF_UP))
                .maintenanceKcalCeiling(request.maintenanceKcalCeiling().setScale(2, RoundingMode.HALF_UP))
                .targetProteinG(request.targetProteinG().setScale(2, RoundingMode.HALF_UP))
                .targetCarbohydrateG(request.targetCarbohydrateG().setScale(2, RoundingMode.HALF_UP))
                .targetLipidG(request.targetLipidG().setScale(2, RoundingMode.HALF_UP))
                .targetFiberG(request.targetFiberG() != null ? request.targetFiberG().setScale(2, RoundingMode.HALF_UP) : new BigDecimal("30.00"))
                .targetWaterMl(request.targetWaterMl() != null ? request.targetWaterMl().setScale(2, RoundingMode.HALF_UP) : new BigDecimal("3500.00"))
                .targetMicronutrients(request.targetMicronutrients() != null ? request.targetMicronutrients() : MicronutrientsData.empty())
                .build();

        DailyTarget saved = dailyTargetRepository.save(target);

        if (Boolean.TRUE.equals(request.generateBaseMeals()) && mealService != null) {
            mealService.generateBaseMealsIfEmpty(effectiveUser);
        }

        return saved;
    }

    @Override
    @Transactional
    public DailyTarget activateTarget(UUID targetId, UUID userId) {
        UUID effectiveUser = (userId != null) ? userId : AppConstants.DEFAULT_USER_ID;

        DailyTarget target = dailyTargetRepository.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("Protocolo de metas não encontrado para ID: " + targetId));

        if (!target.getUserId().equals(effectiveUser)) {
            throw new IllegalArgumentException("O protocolo de metas não pertence ao usuário.");
        }

        // Desativa protocolo ativo existente com flush imediato para o PostgreSQL
        dailyTargetRepository.findByUserIdAndIsActiveTrue(effectiveUser).ifPresent(currentActive -> {
            if (!currentActive.getId().equals(targetId)) {
                currentActive.setActive(false);
                dailyTargetRepository.saveAndFlush(currentActive);
            }
        });

        target.setActive(true);
        return dailyTargetRepository.saveAndFlush(target);
    }

    @Override
    @Transactional
    public void deactivateActiveTarget(UUID userId) {
        UUID effectiveUser = (userId != null) ? userId : AppConstants.DEFAULT_USER_ID;
        dailyTargetRepository.findByUserIdAndIsActiveTrue(effectiveUser).ifPresent(target -> {
            target.setActive(false);
            dailyTargetRepository.saveAndFlush(target);
        });
    }
}
