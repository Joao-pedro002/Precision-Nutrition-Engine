package com.precisionnutrition.config;

import com.precisionnutrition.model.dto.MicronutrientsData;
import com.precisionnutrition.model.entity.*;
import com.precisionnutrition.repository.DailyTargetRepository;
import com.precisionnutrition.repository.FoodRepository;
import com.precisionnutrition.repository.MealItemRepository;
import com.precisionnutrition.repository.MealRepository;
import com.precisionnutrition.util.AppConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.util.List;

@Component
@Profile("!prod")
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class DatabaseSeeder implements CommandLineRunner {

    private final DailyTargetRepository dailyTargetRepository;
    private final MealRepository mealRepository;
    private final MealItemRepository mealItemRepository;
    private final FoodRepository foodRepository;

    @Override
    @Transactional
    public void run(String... args) {
        seedDailyTarget();
        seedMeals();
    }

    private void seedDailyTarget() {
        if (dailyTargetRepository.count() > 0) {
            log.info("DailyTargets já existentes. Pulando seed de alvos.");
            return;
        }

        log.info("Populando Protocolo Padrão: Cutting de Precisão...");

        MicronutrientsData targetMicros = new MicronutrientsData(
                new BigDecimal("1000.00"), // calcium
                new BigDecimal("14.00"),   // iron
                new BigDecimal("400.00"),  // magnesium
                new BigDecimal("3500.00"), // potassium
                new BigDecimal("2000.00"), // sodium
                new BigDecimal("11.00"),   // zinc
                new BigDecimal("90.00"),   // vitamin C
                new BigDecimal("2.40"),    // vitamin B12
                new BigDecimal("300.00")   // cholesterol
        );

        DailyTarget defaultTarget = DailyTarget.builder()
                .userId(AppConstants.DEFAULT_USER_ID)
                .isActive(true)
                .targetKcalMin(new BigDecimal("2000.00"))
                .targetKcalMax(new BigDecimal("2103.00"))
                .maintenanceKcalCeiling(new BigDecimal("2500.00"))
                .targetProteinG(new BigDecimal("180.00"))
                .targetCarbohydrateG(new BigDecimal("210.00"))
                .targetLipidG(new BigDecimal("55.00"))
                .targetFiberG(new BigDecimal("30.00"))
                .targetWaterMl(new BigDecimal("3500.00"))
                .targetMicronutrients(targetMicros)
                .build();

        dailyTargetRepository.save(defaultTarget);
        log.info("Protocolo 'Cutting de Precisão' cadastrado com sucesso.");
    }

    private void seedMeals() {
        if (mealRepository.count() > 0) {
            log.info("Refeições já existentes. Pulando seed de refeições.");
            return;
        }

        long foodCount = foodRepository.count();
        if (foodCount == 0) {
            log.warn("Nenhum alimento cadastrado na tabela foods. Pulando seed de refeições.");
            return;
        }

        log.info("Populando 5 refeições padrão com itens da TACO...");

        Food ovoCozido = findFood("Ovo, de galinha, inteiro, cozido");
        Food claraOvo = findFood("Ovo, de galinha, clara, cozida");
        Food aveia = findFood("Aveia, flocos, crua");
        Food bananaPrata = findFood("Banana, prata, crua");
        Food frangoGrelhado = findFood("Frango, peito, sem pele, grelhado");
        Food patinhoGrelhado = findFood("Carne, bovina, patinho, sem gordura, grelhado");
        Food arrozCozido = findFood("Arroz, tipo 1, cozido");
        Food feijaoCarioca = findFood("Feijão, carioca, cozido");
        Food azeiteOliva = findFood("Azeite, de oliva, extravirgem");
        Food batataDoce = findFood("Batata, doce, cozida");
        Food paoFrances = findFood("Pão, francês");
        Food leiteDesnatado = findFood("Leite, de vaca, pasteurizado, desnatado");

        // 1. Café da Manhã (07:30)
        Meal cafe = Meal.builder()
                .userId(AppConstants.DEFAULT_USER_ID)
                .name("Café da Manhã")
                .orderIndex(1)
                .targetTime(LocalTime.of(7, 30))
                .build();
        mealRepository.save(cafe);

        createItem(cafe, ovoCozido, 1, new BigDecimal("150.00"));
        createItem(cafe, aveia, 1, new BigDecimal("40.00"));
        createItem(cafe, bananaPrata, 1, new BigDecimal("100.00"));

        createItem(cafe, claraOvo, 2, new BigDecimal("200.00"));
        createItem(cafe, paoFrances, 2, new BigDecimal("50.00"));
        createItem(cafe, leiteDesnatado, 2, new BigDecimal("200.00"));

        // 2. Almoço (12:30)
        Meal almoco = Meal.builder()
                .userId(AppConstants.DEFAULT_USER_ID)
                .name("Almoço")
                .orderIndex(2)
                .targetTime(LocalTime.of(12, 30))
                .build();
        mealRepository.save(almoco);

        createItem(almoco, frangoGrelhado, 1, new BigDecimal("160.00"));
        createItem(almoco, arrozCozido, 1, new BigDecimal("150.00"));
        createItem(almoco, feijaoCarioca, 1, new BigDecimal("100.00"));
        createItem(almoco, azeiteOliva, 1, new BigDecimal("8.00"));

        createItem(almoco, patinhoGrelhado, 2, new BigDecimal("150.00"));
        createItem(almoco, batataDoce, 2, new BigDecimal("200.00"));
        createItem(almoco, azeiteOliva, 2, new BigDecimal("5.00"));

        // 3. Lanche da Tarde (16:00)
        Meal lanche = Meal.builder()
                .userId(AppConstants.DEFAULT_USER_ID)
                .name("Lanche da Tarde")
                .orderIndex(3)
                .targetTime(LocalTime.of(16, 0))
                .build();
        mealRepository.save(lanche);

        createItem(lanche, leiteDesnatado, 1, new BigDecimal("250.00"));
        createItem(lanche, aveia, 1, new BigDecimal("30.00"));
        createItem(lanche, bananaPrata, 1, new BigDecimal("80.00"));

        createItem(lanche, ovoCozido, 2, new BigDecimal("100.00"));
        createItem(lanche, paoFrances, 2, new BigDecimal("50.00"));

        // 4. Jantar (19:30)
        Meal jantar = Meal.builder()
                .userId(AppConstants.DEFAULT_USER_ID)
                .name("Jantar")
                .orderIndex(4)
                .targetTime(LocalTime.of(19, 30))
                .build();
        mealRepository.save(jantar);

        createItem(jantar, frangoGrelhado, 1, new BigDecimal("160.00"));
        createItem(jantar, arrozCozido, 1, new BigDecimal("120.00"));
        createItem(jantar, feijaoCarioca, 1, new BigDecimal("100.00"));
        createItem(jantar, azeiteOliva, 1, new BigDecimal("8.00"));

        createItem(jantar, patinhoGrelhado, 2, new BigDecimal("150.00"));
        createItem(jantar, batataDoce, 2, new BigDecimal("180.00"));
        createItem(jantar, azeiteOliva, 2, new BigDecimal("5.00"));

        // 5. Ceia (22:00)
        Meal ceia = Meal.builder()
                .userId(AppConstants.DEFAULT_USER_ID)
                .name("Ceia")
                .orderIndex(5)
                .targetTime(LocalTime.of(22, 0))
                .build();
        mealRepository.save(ceia);

        createItem(ceia, ovoCozido, 1, new BigDecimal("100.00"));
        createItem(ceia, bananaPrata, 1, new BigDecimal("80.00"));

        createItem(ceia, leiteDesnatado, 2, new BigDecimal("200.00"));
        createItem(ceia, aveia, 2, new BigDecimal("25.00"));

        log.info("5 refeições padrão populadas com sucesso.");
    }

    private Food findFood(String query) {
        List<Food> foods = foodRepository.searchByName(query, PageRequest.of(0, 1)).getContent();
        if (!foods.isEmpty()) {
            return foods.get(0);
        }
        // Fallback: busca qualquer alimento
        return foodRepository.findAll(PageRequest.of(0, 1)).getContent().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Nenhum alimento cadastrado no banco de dados!"));
    }

    private void createItem(Meal meal, Food food, int optionNumber, BigDecimal weightG) {
        MealItem item = MealItem.builder()
                .meal(meal)
                .food(food)
                .optionNumber(optionNumber)
                .weightG(weightG.setScale(2, RoundingMode.HALF_UP))
                .build();
        mealItemRepository.save(item);
    }
}
