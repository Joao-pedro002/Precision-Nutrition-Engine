package com.precisionnutrition.service;

import com.precisionnutrition.model.dto.MicronutrientsData;
import com.precisionnutrition.model.dto.TargetCalculationRequest;
import com.precisionnutrition.model.dto.TargetCalculationResponse;
import com.precisionnutrition.model.entity.DailyTarget;
import com.precisionnutrition.model.enums.Gender;
import com.precisionnutrition.model.enums.TargetSource;
import com.precisionnutrition.repository.DailyTargetRepository;
import com.precisionnutrition.util.AppConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TargetWizardServiceImpl implements TargetWizardService {

    private final DailyTargetRepository dailyTargetRepository;

    @Override
    public TargetCalculationResponse calculateTargets(TargetCalculationRequest request) {
        // Normalização de altura: se menor que 3.0, assumir metros e converter para centímetros
        BigDecimal heightCm = request.heightCm();
        if (heightCm.compareTo(new BigDecimal("3.0")) < 0) {
            heightCm = heightCm.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal weightKg = request.weightKg();
        BigDecimal age = BigDecimal.valueOf(request.ageYears());

        // Equação de Mifflin-St Jeor:
        // Homens: 10 * peso + 6.25 * altura_cm - 5 * idade + 5
        // Mulheres: 10 * peso + 6.25 * altura_cm - 5 * idade - 161
        BigDecimal partWeight = new BigDecimal("10").multiply(weightKg);
        BigDecimal partHeight = new BigDecimal("6.25").multiply(heightCm);
        BigDecimal partAge = new BigDecimal("5").multiply(age);

        BigDecimal bmr;
        if (request.gender() == Gender.MALE) {
            bmr = partWeight.add(partHeight).subtract(partAge).add(new BigDecimal("5"));
        } else {
            bmr = partWeight.add(partHeight).subtract(partAge).subtract(new BigDecimal("161"));
        }
        bmr = bmr.setScale(2, RoundingMode.HALF_UP);

        // TDEE = BMR * Fator de Atividade
        BigDecimal tdee = bmr.multiply(request.activityLevel().getMultiplier()).setScale(2, RoundingMode.HALF_UP);

        // Multiplicador de proteína dinâmico ou padrão clínico por objetivo
        BigDecimal effectiveProteinMultiplier = request.proteinMultiplier();
        if (effectiveProteinMultiplier == null || effectiveProteinMultiplier.compareTo(BigDecimal.ZERO) <= 0) {
            effectiveProteinMultiplier = switch (request.goal()) {
                case LOSE_FAT -> new BigDecimal("2.20");
                case CLEAN_BULK, MAINTAIN -> new BigDecimal("2.00");
            };
        }
        effectiveProteinMultiplier = effectiveProteinMultiplier.setScale(2, RoundingMode.HALF_UP);

        // Definição de zonas e macros conforme objetivo
        BigDecimal targetKcalMin;
        BigDecimal targetKcalMax;
        BigDecimal maintenanceKcalCeiling;
        BigDecimal targetProteinG;
        BigDecimal targetLipidG;
        BigDecimal targetFiberG;
        BigDecimal targetWaterMl;

        switch (request.goal()) {
            case LOSE_FAT -> {
                // Déficit calórico: 20% do TDEE
                targetKcalMax = tdee.multiply(new BigDecimal("0.80")).setScale(2, RoundingMode.HALF_UP);
                targetKcalMin = targetKcalMax.subtract(new BigDecimal("100.00")).setScale(2, RoundingMode.HALF_UP);
                maintenanceKcalCeiling = tdee;
                // Proteína customizada ou padrão no cutting (2.2g/kg)
                targetProteinG = weightKg.multiply(effectiveProteinMultiplier).setScale(2, RoundingMode.HALF_UP);
                // Gordura moderada/baixa: 0.8g/kg
                targetLipidG = weightKg.multiply(new BigDecimal("0.80")).setScale(2, RoundingMode.HALF_UP);
                targetFiberG = new BigDecimal("30.00");
                targetWaterMl = weightKg.multiply(new BigDecimal("45.00")).max(new BigDecimal("3500.00")).setScale(2, RoundingMode.HALF_UP);
            }
            case CLEAN_BULK -> {
                // Superávit calórico limpo: +10% do TDEE
                targetKcalMax = tdee.multiply(new BigDecimal("1.10")).setScale(2, RoundingMode.HALF_UP);
                targetKcalMin = tdee;
                maintenanceKcalCeiling = targetKcalMax.add(new BigDecimal("200.00")).setScale(2, RoundingMode.HALF_UP);
                targetProteinG = weightKg.multiply(effectiveProteinMultiplier).setScale(2, RoundingMode.HALF_UP);
                targetLipidG = weightKg.multiply(new BigDecimal("1.00")).setScale(2, RoundingMode.HALF_UP);
                targetFiberG = new BigDecimal("35.00");
                targetWaterMl = weightKg.multiply(new BigDecimal("45.00")).max(new BigDecimal("3500.00")).setScale(2, RoundingMode.HALF_UP);
            }
            case MAINTAIN -> {
                targetKcalMax = tdee;
                targetKcalMin = tdee.subtract(new BigDecimal("100.00")).setScale(2, RoundingMode.HALF_UP);
                maintenanceKcalCeiling = tdee.multiply(new BigDecimal("1.10")).setScale(2, RoundingMode.HALF_UP);
                targetProteinG = weightKg.multiply(effectiveProteinMultiplier).setScale(2, RoundingMode.HALF_UP);
                targetLipidG = weightKg.multiply(new BigDecimal("1.00")).setScale(2, RoundingMode.HALF_UP);
                targetFiberG = new BigDecimal("30.00");
                targetWaterMl = weightKg.multiply(new BigDecimal("40.00")).max(new BigDecimal("3000.00")).setScale(2, RoundingMode.HALF_UP);
            }
            default -> throw new IllegalArgumentException("Objetivo não suportado: " + request.goal());
        }

        // Carboidratos = (targetKcalMax - (4 * Proteína) - (9 * Gordura)) / 4
        BigDecimal proteinKcal = targetProteinG.multiply(new BigDecimal("4.00"));
        BigDecimal lipidKcal = targetLipidG.multiply(new BigDecimal("9.00"));
        BigDecimal remainingKcal = targetKcalMax.subtract(proteinKcal).subtract(lipidKcal);

        BigDecimal targetCarbohydrateG = remainingKcal.divide(new BigDecimal("4.00"), 2, RoundingMode.HALF_UP);
        if (targetCarbohydrateG.compareTo(new BigDecimal("20.00")) < 0) {
            targetCarbohydrateG = new BigDecimal("20.00");
        }

        // Matriz de Micronutrientes padrão recomendados (RDA)
        MicronutrientsData targetMicronutrients = new MicronutrientsData(
                new BigDecimal("1000.00"), // cálcio
                (request.gender() == Gender.FEMALE) ? new BigDecimal("18.00") : new BigDecimal("14.00"), // ferro
                new BigDecimal("400.00"),  // magnésio
                new BigDecimal("3500.00"), // potássio
                new BigDecimal("2000.00"), // sódio
                (request.gender() == Gender.FEMALE) ? new BigDecimal("8.00") : new BigDecimal("11.00"),   // zinco
                new BigDecimal("90.00"),   // vit c
                new BigDecimal("2.40"),    // vit b12
                new BigDecimal("300.00")   // colesterol
        );

        return new TargetCalculationResponse(
                request.gender(),
                request.ageYears(),
                weightKg,
                heightCm,
                request.activityLevel(),
                request.goal(),
                bmr,
                tdee,
                targetKcalMin,
                targetKcalMax,
                maintenanceKcalCeiling,
                targetProteinG,
                targetCarbohydrateG,
                targetLipidG,
                targetFiberG,
                targetWaterMl,
                targetMicronutrients,
                TargetSource.CALCULATED,
                effectiveProteinMultiplier
        );
    }

    @Override
    @Transactional
    public DailyTarget calculateAndApply(TargetCalculationRequest request) {
        UUID effectiveUser = (request.userId() != null) ? request.userId() : AppConstants.DEFAULT_USER_ID;

        TargetCalculationResponse calculated = calculateTargets(request);

        // Desativa protocolo ativo anterior do usuário com flush imediato para o PostgreSQL
        dailyTargetRepository.findByUserIdAndIsActiveTrue(effectiveUser).ifPresent(previousActive -> {
            previousActive.setActive(false);
            dailyTargetRepository.saveAndFlush(previousActive);
            log.info("Desativado protocolo anterior id={}", previousActive.getId());
        });

        DailyTarget newTarget = DailyTarget.builder()
                .userId(effectiveUser)
                .isActive(true)
                .targetSource(TargetSource.CALCULATED)
                .targetKcalMin(calculated.targetKcalMin())
                .targetKcalMax(calculated.targetKcalMax())
                .maintenanceKcalCeiling(calculated.maintenanceKcalCeiling())
                .targetProteinG(calculated.targetProteinG())
                .targetCarbohydrateG(calculated.targetCarbohydrateG())
                .targetLipidG(calculated.targetLipidG())
                .targetFiberG(calculated.targetFiberG())
                .targetWaterMl(calculated.targetWaterMl())
                .targetMicronutrients(calculated.targetMicronutrients())
                .build();

        DailyTarget saved = dailyTargetRepository.save(newTarget);
        log.info("Novo protocolo CALCULATED salvo com sucesso: id={}, targetKcalMax={} kcal", saved.getId(), saved.getTargetKcalMax());
        return saved;
    }
}
