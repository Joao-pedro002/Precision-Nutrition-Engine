package com.precisionnutrition.service;

import com.precisionnutrition.model.dto.TargetCalculationRequest;
import com.precisionnutrition.model.dto.TargetCalculationResponse;
import com.precisionnutrition.model.entity.DailyTarget;
import com.precisionnutrition.model.enums.ActivityLevel;
import com.precisionnutrition.model.enums.DietGoal;
import com.precisionnutrition.model.enums.Gender;
import com.precisionnutrition.model.enums.TargetSource;
import com.precisionnutrition.repository.DailyTargetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TargetWizardServiceTest {

    @Mock
    private DailyTargetRepository dailyTargetRepository;

    private TargetWizardService wizardService;

    @BeforeEach
    void setUp() {
        wizardService = new TargetWizardServiceImpl(dailyTargetRepository);
    }

    @Test
    @DisplayName("Épica 1: Deve calcular BMR e TDEE via Mifflin-St Jeor para homem de 20 anos, 68kg, 1.73m, moderado (~2582 kcal)")
    void testMifflinStJeorMaleModeratelyActive() {
        // Homem de 20 anos, 68 kg, 1.73m, atividade moderada
        TargetCalculationRequest request = new TargetCalculationRequest(
                UUID.randomUUID(),
                Gender.MALE,
                20,
                new BigDecimal("68.00"),
                new BigDecimal("1.73"), // Teste com altura em metros para validar auto-normalização
                ActivityLevel.MODERATE,
                DietGoal.LOSE_FAT
        );

        TargetCalculationResponse response = wizardService.calculateTargets(request);

        assertNotNull(response);
        // BMR = 10 * 68 + 6.25 * 173 - 5 * 20 + 5 = 680 + 1081.25 - 100 + 5 = 1666.25
        assertEquals(new BigDecimal("1666.25"), response.bmrKcal());

        // TDEE = 1666.25 * 1.55 = 2582.6875 -> 2582.69 kcal (~2582 kcal)
        assertEquals(new BigDecimal("2582.69"), response.tdeeKcal());

        // Cutting (LOSE_FAT): 20% deficit
        // Max = 2582.69 * 0.80 = 2066.15
        assertEquals(new BigDecimal("2066.15"), response.targetKcalMax());
        assertEquals(new BigDecimal("1966.15"), response.targetKcalMin());
        assertEquals(new BigDecimal("2582.69"), response.maintenanceKcalCeiling());

        // Macros Cutting: Proteína 2.2g/kg = 149.60g, Lipídios 0.8g/kg = 54.40g
        assertEquals(new BigDecimal("149.60"), response.targetProteinG());
        assertEquals(new BigDecimal("54.40"), response.targetLipidG());
        assertTrue(response.targetCarbohydrateG().compareTo(BigDecimal.ZERO) > 0);
        assertEquals(TargetSource.CALCULATED, response.targetSource());
    }

    @Test
    @DisplayName("Épica 1: Deve calcular BMR e TDEE para mulher de 25 anos, 60kg, 165cm, levemente ativa")
    void testMifflinStJeorFemaleLightlyActive() {
        // BMR = 10 * 60 + 6.25 * 165 - 5 * 25 - 161 = 600 + 1031.25 - 125 - 161 = 1345.25
        // TDEE = 1345.25 * 1.375 = 1849.71875 -> 1849.72 kcal
        TargetCalculationRequest request = new TargetCalculationRequest(
                UUID.randomUUID(),
                Gender.FEMALE,
                25,
                new BigDecimal("60.00"),
                new BigDecimal("165.00"),
                ActivityLevel.LIGHT,
                DietGoal.MAINTAIN
        );

        TargetCalculationResponse response = wizardService.calculateTargets(request);

        assertNotNull(response);
        assertEquals(new BigDecimal("1345.25"), response.bmrKcal());
        assertEquals(new BigDecimal("1849.72"), response.tdeeKcal());
        assertEquals(new BigDecimal("1849.72"), response.targetKcalMax());
    }

    @Test
    @DisplayName("Épica 1: Deve desativar protocolo anterior e persistir nova meta CALCULATED no calculateAndApply")
    void testCalculateAndApply() {
        UUID userId = UUID.randomUUID();
        DailyTarget previousActive = DailyTarget.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .isActive(true)
                .build();

        when(dailyTargetRepository.findByUserIdAndIsActiveTrue(userId)).thenReturn(Optional.of(previousActive));
        when(dailyTargetRepository.saveAndFlush(any(DailyTarget.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(dailyTargetRepository.save(any(DailyTarget.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TargetCalculationRequest request = new TargetCalculationRequest(
                userId,
                Gender.MALE,
                22,
                new BigDecimal("75.00"),
                new BigDecimal("180.00"),
                ActivityLevel.MODERATE,
                DietGoal.CLEAN_BULK
        );

        DailyTarget result = wizardService.calculateAndApply(request);

        assertNotNull(result);
        assertTrue(result.isActive());
        assertEquals(TargetSource.CALCULATED, result.getTargetSource());
        assertFalse(previousActive.isActive(), "Protocolo anterior deve ter sido desativado");
        verify(dailyTargetRepository, times(1)).saveAndFlush(previousActive);
        verify(dailyTargetRepository, times(1)).save(any(DailyTarget.class));
    }

    @Test
    @DisplayName("Épica 1: Deve respeitar proteinMultiplier dinâmico customizado")
    void testCustomProteinMultiplier() {
        TargetCalculationRequest request = new TargetCalculationRequest(
                UUID.randomUUID(),
                Gender.MALE,
                25,
                new BigDecimal("80.00"),
                new BigDecimal("180.00"),
                ActivityLevel.MODERATE,
                DietGoal.LOSE_FAT,
                new BigDecimal("2.50") // Multiplicador customizado 2.5 g/kg
        );

        TargetCalculationResponse response = wizardService.calculateTargets(request);

        assertNotNull(response);
        // 80kg * 2.50 = 200.00g de proteína
        assertEquals(new BigDecimal("200.00"), response.targetProteinG());
        assertEquals(new BigDecimal("2.50"), response.proteinMultiplier());
    }
}
