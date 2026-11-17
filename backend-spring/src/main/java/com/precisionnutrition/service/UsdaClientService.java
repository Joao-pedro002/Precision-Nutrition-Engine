package com.precisionnutrition.service;

import com.precisionnutrition.model.entity.Food;

import java.util.List;

public interface UsdaClientService {

    /**
     * Pesquisa alimentos na API USDA FoodData Central, converte os nutrientes oficiais
     * e realiza o cache automático (upsert) na tabela 'foods' do PostgreSQL com source = 'USDA'.
     */
    List<Food> searchAndCacheUsdaFoods(String query, int pageSize);

    /**
     * Importa diretamente um alimento específico da USDA por seu fdcId e salva em cache.
     */
    Food importByFdcId(Long fdcId);
}
