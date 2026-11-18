package com.precisionnutrition.service;

import com.precisionnutrition.exception.IncompatibleFoodSubstitutionException;
import com.precisionnutrition.model.dto.*;
import com.precisionnutrition.model.entity.Food;
import com.precisionnutrition.model.entity.FoodPortion;
import com.precisionnutrition.model.enums.CalorieZone;
import com.precisionnutrition.model.enums.DominantMacro;
import com.precisionnutrition.model.enums.FoodSource;
import com.precisionnutrition.model.enums.MacroTarget;
import com.precisionnutrition.repository.FoodRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class NutritionMathServiceTest {

    private NutritionMathService mathService;

    @BeforeEach
    void setUp() {
        mathService = new NutritionMathServiceImpl();
    }

    @Test
    @DisplayName("Deve calcular proporcionalidade linear de 100g com precisão de 2 casas decimais")
    void testScale100g() {
        BigDecimal value100g = new BigDecimal("159.00");
        BigDecimal weightG = new BigDecimal("150.00");

        BigDecimal scaled = mathService.scale100g(value100g, weightG);
        assertEquals(new BigDecimal("238.50"), scaled);
    }

    @Test
    @DisplayName("Deve auditar divergência de Atwater dentro da margem aceitável de 15%")
    void testAtwaterAuditWithinTolerance() {
        // Frango grelhado: 159 kcal declaradas, P: 32g, C: 0g, G: 2.5g
        // Atwater = 32*4 + 0*4 + 2.5*9 = 128 + 22.5 = 150.5 kcal
        // Divergência = |159 - 150.5| / 159 = 8.5 / 159 = ~5.35% <= 15%
        NutrientAuditResult result = mathService.auditAtwaterDivergence(
                new BigDecimal("159.00"),
                new BigDecimal("32.00"),
                BigDecimal.ZERO,
                new BigDecimal("2.50")
        );

        assertNotNull(result);
        assertEquals(new BigDecimal("150.50"), result.atwaterKcal());
        assertFalse(result.atwaterDivergence(), "Divergência de 5.35% deve estar dentro da tolerância de 15%");
    }

    @Test
    @DisplayName("Deve alertar divergência de Atwater quando exceder 15%")
    void testAtwaterAuditExceedingTolerance() {
        // Alimento com erro no laudo: 250 kcal declaradas, mas apenas P: 10g, C: 10g, G: 2g (Atwater = 98 kcal)
        NutrientAuditResult result = mathService.auditAtwaterDivergence(
                new BigDecimal("250.00"),
                new BigDecimal("10.00"),
                new BigDecimal("10.00"),
                new BigDecimal("2.00")
        );

        assertTrue(result.atwaterDivergence(), "Divergência de ~60% deve disparar o alerta de auditoria");
    }

    @Test
    @DisplayName("Deve calcular a razão molar/massa entre Sódio e Potássio")
    void testSodiumPotassiumRatio() {
        BigDecimal sodium = new BigDecimal("50.00");
        BigDecimal potassium = new BigDecimal("386.00");

        BigDecimal ratio = mathService.calculateSodiumPotassiumRatio(sodium, potassium);
        assertNotNull(ratio);
        assertEquals(new BigDecimal("0.13"), ratio);
    }

    @Test
    @DisplayName("Deve classificar corretamente as 4 zonas calóricas progressivas")
    void testCaloricZoneClassification() {
        BigDecimal min = new BigDecimal("2000.00");
        BigDecimal max = new BigDecimal("2103.00");
        BigDecimal ceiling = new BigDecimal("2500.00");

        // Abaixo do piso
        CaloricZoneResult r1 = mathService.determineCaloricZone(new BigDecimal("1850.00"), min, max, ceiling);
        assertEquals(CalorieZone.BELOW_TARGET, r1.zone());

        // Dentro do déficit ótimo
        CaloricZoneResult r2 = mathService.determineCaloricZone(new BigDecimal("2050.00"), min, max, ceiling);
        assertEquals(CalorieZone.OPTIMAL_DEFICIT, r2.zone());

        // Na margem de segurança até manutenção
        CaloricZoneResult r3 = mathService.determineCaloricZone(new BigDecimal("2300.00"), min, max, ceiling);
        assertEquals(CalorieZone.MAINTENANCE_MARGIN, r3.zone());

        // Rompimento do teto (Superávit)
        CaloricZoneResult r4 = mathService.determineCaloricZone(new BigDecimal("2600.00"), min, max, ceiling);
        assertEquals(CalorieZone.SURPLUS, r4.zone());
    }

    @Test
    @DisplayName("Substituição Crítica: 150g de Frango por Ovo Cozido DEVE disparar Calorie Drift Warning")
    void testChickenToEggSubstitutionTriggersCalorieDrift() {
        // Alimento A: Peito de Frango Grelhado (PROTEIN)
        Food chicken = Food.builder()
                .id(UUID.randomUUID())
                .name("Frango, peito, sem pele, grelhado")
                .source(FoodSource.TACO)
                .externalId("TACO-005")
                .dominantMacro(DominantMacro.PROTEIN)
                .servingSizeG(new BigDecimal("100.00"))
                .energyKcal(new BigDecimal("159.00"))
                .proteinG(new BigDecimal("32.00"))
                .carbohydrateG(BigDecimal.ZERO)
                .lipidG(new BigDecimal("2.50"))
                .fiberG(BigDecimal.ZERO)
                .micronutrients(MicronutrientsData.empty())
                .build();

        // Alimento B: Ovo Inteiro Cozido (FAT)
        Food egg = Food.builder()
                .id(UUID.randomUUID())
                .name("Ovo, de galinha, inteiro, cozido")
                .source(FoodSource.TACO)
                .externalId("TACO-010")
                .dominantMacro(DominantMacro.FAT)
                .servingSizeG(new BigDecimal("100.00"))
                .energyKcal(new BigDecimal("146.00"))
                .proteinG(new BigDecimal("13.00"))
                .carbohydrateG(new BigDecimal("0.60"))
                .lipidG(new BigDecimal("9.50"))
                .fiberG(BigDecimal.ZERO)
                .micronutrients(MicronutrientsData.empty())
                .build();

        // 150g de Frango possui 48.00g de proteína e 238.50 kcal
        BigDecimal chickenWeight = new BigDecimal("150.00");

        SubstitutionResult result = mathService.calculateEquivalentSubstitution(
                chicken,
                chickenWeight,
                egg,
                null // Sem override: Origem (PROTEIN) rege a substituição
        );

        assertNotNull(result);
        assertEquals(MacroTarget.PROTEIN, result.matchedMacro());

        // Peso do ovo necessário = (48g / 13g) * 100 = 369.23g
        assertEquals(new BigDecimal("369.23"), result.targetFood().weightG());

        // Proteína do ovo deve ser igualada em 48.00g
        assertEquals(new BigDecimal("48.00"), result.targetFood().proteinG());

        // Calorias do ovo disparam para ~539.08 kcal (Delta = +300.58 kcal)
        assertTrue(result.deltaKcal().compareTo(new BigDecimal("300.00")) > 0, "Delta calórico deve ser maior que 300 kcal");
        assertTrue(result.calorieDriftPercent().compareTo(new BigDecimal("100.00")) > 0, "Drift percentual deve exceder 100%");

        // Trava de Calorie Drift DEVE ser acionada
        assertTrue(result.calorieDriftWarning(), "Substituir Peito de Frango por Ovo Inteiro igualando proteína DEVE gerar Calorie Drift Warning!");
        assertNotNull(result.warningMessage());
    }

    @Test
    @DisplayName("Deve rejeitar com exceção substituição em alimento com concentração insuficiente do macro")
    void testIncompatibleFoodThrowsException() {
        Food chicken = Food.builder()
                .name("Frango")
                .dominantMacro(DominantMacro.PROTEIN)
                .proteinG(new BigDecimal("32.00"))
                .energyKcal(new BigDecimal("159.00"))
                .build();

        Food oliveOil = Food.builder()
                .name("Azeite de Oliva")
                .dominantMacro(DominantMacro.FAT)
                .proteinG(BigDecimal.ZERO) // 0g de proteína
                .energyKcal(new BigDecimal("884.00"))
                .build();

        assertThrows(IncompatibleFoodSubstitutionException.class, () ->
                mathService.calculateEquivalentSubstitution(chicken, new BigDecimal("100.00"), oliveOil, null)
        );
    }

    @Test
    @DisplayName("Regra 2: Origem BALANCED com Proteína >= 5.0g deve usar PROTEIN como fallback padrão")
    void testBalancedOriginWithHighProteinDefaultsToProtein() {
        Food balancedHighProtein = Food.builder()
                .name("Suplemento Misto")
                .dominantMacro(DominantMacro.BALANCED)
                .proteinG(new BigDecimal("10.00")) // >= 5.0g
                .energyKcal(new BigDecimal("150.00"))
                .build();

        Food chicken = Food.builder()
                .name("Frango")
                .dominantMacro(DominantMacro.PROTEIN)
                .proteinG(new BigDecimal("32.00"))
                .energyKcal(new BigDecimal("159.00"))
                .build();

        SubstitutionResult result = mathService.calculateEquivalentSubstitution(
                balancedHighProtein,
                new BigDecimal("100.00"),
                chicken,
                null
        );

        assertEquals(MacroTarget.PROTEIN, result.matchedMacro());
    }

    @Test
    @DisplayName("Regra 2: Origem BALANCED com Proteína < 5.0g deve usar KCAL como fallback padrão")
    void testBalancedOriginWithLowProteinDefaultsToKcal() {
        Food balancedLowProtein = Food.builder()
                .name("Bebida Láctea Leve")
                .dominantMacro(DominantMacro.BALANCED)
                .proteinG(new BigDecimal("3.00")) // < 5.0g
                .energyKcal(new BigDecimal("60.00"))
                .build();

        Food wholeMilk = Food.builder()
                .name("Leite Integral")
                .dominantMacro(DominantMacro.BALANCED)
                .proteinG(new BigDecimal("3.10"))
                .energyKcal(new BigDecimal("60.00"))
                .build();

        SubstitutionResult result = mathService.calculateEquivalentSubstitution(
                balancedLowProtein,
                new BigDecimal("200.00"),
                wholeMilk,
                null
        );

        assertEquals(MacroTarget.KCAL, result.matchedMacro());
    }

    @Test
    @DisplayName("Épica 2: Deve retornar Top 5 alimentos alternativos ordenados pelo menor Calorie Drift")
    void testQuickSubstitutionsOrderedByMinimalCalorieDrift() {
        FoodRepository mockRepo = Mockito.mock(FoodRepository.class);
        NutritionMathService serviceWithRepo = new NutritionMathServiceImpl(mockRepo);

        UUID sourceId = UUID.randomUUID();
        Food chickenBreast = Food.builder()
                .id(sourceId)
                .name("Peito de Frango")
                .foodGroup("PROTEINA_ANIMAL")
                .dominantMacro(DominantMacro.PROTEIN)
                .proteinG(new BigDecimal("32.00"))
                .carbohydrateG(BigDecimal.ZERO)
                .lipidG(new BigDecimal("2.50"))
                .energyKcal(new BigDecimal("159.00"))
                .build();

        // Candidato 1: Tilápia (Praticamente sem gordura extra -> menor drift)
        Food tilapia = Food.builder()
                .id(UUID.randomUUID())
                .name("Tilápia Grelhada")
                .foodGroup("PROTEINA_ANIMAL")
                .dominantMacro(DominantMacro.PROTEIN)
                .proteinG(new BigDecimal("26.00"))
                .carbohydrateG(BigDecimal.ZERO)
                .lipidG(new BigDecimal("2.00"))
                .energyKcal(new BigDecimal("128.00"))
                .portions(List.of(FoodPortion.builder().portionName("1 Filé").weightG(new BigDecimal("120.00")).build()))
                .build();

        // Candidato 2: Ovo Cozido (Possui mais gordura -> maior drift)
        Food egg = Food.builder()
                .id(UUID.randomUUID())
                .name("Ovo Cozido")
                .foodGroup("PROTEINA_ANIMAL")
                .dominantMacro(DominantMacro.PROTEIN)
                .proteinG(new BigDecimal("13.00"))
                .carbohydrateG(new BigDecimal("1.00"))
                .lipidG(new BigDecimal("10.00"))
                .energyKcal(new BigDecimal("146.00"))
                .portions(List.of(FoodPortion.builder().portionName("1 Unidade").weightG(new BigDecimal("50.00")).build()))
                .build();

        when(mockRepo.findById(sourceId)).thenReturn(Optional.of(chickenBreast));
        when(mockRepo.findCandidatesByFoodGroup(eq("PROTEINA_ANIMAL"), eq(sourceId), any(Pageable.class)))
                .thenReturn(List.of(egg, tilapia));

        List<QuickSubstitutionDto> quickSubs = serviceWithRepo.getQuickSubstitutions(
                sourceId,
                new BigDecimal("150.00"),
                null,
                5
        );

        assertNotNull(quickSubs);
        assertEquals(2, quickSubs.size());
        // Tilápia deve vir primeiro por ter menor módulo de Calorie Drift que o Ovo
        assertEquals("Tilápia Grelhada", quickSubs.get(0).targetFoodName());
        assertEquals("Ovo Cozido", quickSubs.get(1).targetFoodName());
        assertTrue(quickSubs.get(0).deltaKcal().abs().compareTo(quickSubs.get(1).deltaKcal().abs()) < 0);
        assertFalse(quickSubs.get(0).portions().isEmpty());
    }
}
