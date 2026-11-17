package com.precisionnutrition.service;

import com.precisionnutrition.model.dto.*;
import com.precisionnutrition.model.entity.Food;
import com.precisionnutrition.model.enums.DominantMacro;
import com.precisionnutrition.model.enums.MacroTarget;

import java.math.BigDecimal;
import java.util.UUID;

public interface NutritionMathService {

    /**
     * Aplica a fórmula linear proporcional para escalonar valor de 100g para peso arbitrário:
     * (Valor100g / 100) * pesoG
     */
    BigDecimal scale100g(BigDecimal valuePer100g, BigDecimal weightG);

    /**
     * Calcula o resumo de macronutrientes proporcionais para a porção especificada.
     */
    FoodSummary calculatePortionMacros(Food food, BigDecimal weightG);

    /**
     * Escala todas as 9 chaves do contrato de micronutrientes para o peso especificado.
     */
    MicronutrientsData calculatePortionMicronutrients(MicronutrientsData micros100g, BigDecimal weightG);

    /**
     * Executa a auditoria de Atwater (4P + 4C + 9G) e verifica tolerância de divergência de ±15%.
     */
    NutrientAuditResult auditAtwaterDivergence(BigDecimal energyKcal, BigDecimal proteinG, BigDecimal carbG, BigDecimal lipidG);

    /**
     * Atalho para auditoria de Atwater a partir de uma entidade Food.
     */
    NutrientAuditResult auditAtwaterEnergy(Food food);

    /**
     * Computa a razão eletrolítica entre Sódio e Potássio (Na:K).
     */
    BigDecimal calculateSodiumPotassiumRatio(BigDecimal sodiumMg, BigDecimal potassiumMg);

    /**
     * Determina a zona de progresso calórico do usuário (Piso do Déficit, Margem de Manutenção, Superávit).
     */
    CaloricZoneResult determineCaloricZone(BigDecimal consumedKcal, BigDecimal targetKcalMin, BigDecimal targetKcalMax, BigDecimal maintenanceKcalCeiling);

    /**
     * Alias para determineCaloricZone.
     */
    CaloricZoneResult evaluateCaloricZone(BigDecimal consumedKcal, BigDecimal targetKcalMin, BigDecimal targetKcalMax, BigDecimal maintenanceKcalCeiling);

    /**
     * Executa a Calculadora de Substituição Equivalente Bidirecional com estratégia híbrida determinística,
     * proteção biológica contra divisão por zero e alerta de Calorie Drift (|Δkcal| > 10%).
     */
    SubstitutionResult calculateEquivalentSubstitution(Food sourceFood, BigDecimal sourceWeightG, Food targetFood, MacroTarget targetMacroOverride);

    /**
     * Sobrecarga que aceita IDs de alimentos para execução em camadas de controle/API.
     */
    SubstitutionResult calculateSubstitution(UUID sourceFoodId, BigDecimal sourceWeightG, UUID targetFoodId, MacroTarget targetMacroOverride);

    /**
     * Retorna uma instância transitória de Food com todos os nutrientes e micronutrientes
     * proporcionalmente escalonados para o peso informado em gramas.
     */
    Food calculateProportionalNutrition(Food food, BigDecimal weightG);

    /**
     * Calcula dinamicamente o macro dominante com base nas calorias contribuídas (4P, 4C, 9G).
     */
    DominantMacro calculateDominantMacro(BigDecimal proteinG, BigDecimal carbG, BigDecimal lipidG);
}
