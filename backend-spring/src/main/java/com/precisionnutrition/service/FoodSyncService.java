package com.precisionnutrition.service;

import com.precisionnutrition.model.entity.Food;

import java.util.List;

public interface FoodSyncService {

    /**
     * Orquestra a busca de alimentos com Read-Through Cache:
     * 1. Busca primeiro localmente em 'foods' via findByNameContainingIgnoreCase
     * 2. Se o resultado local for vazio ou possuir menos de 3 itens, chama UsdaApiClient
     * 3. Mapeia e persiste os novos alimentos com source = USDA e baseAmount = 100g
     * 4. Retorna a lista combinada formatada
     */
    List<Food> searchFoods(String query);
}
