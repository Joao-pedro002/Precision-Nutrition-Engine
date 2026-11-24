package com.precisionnutrition.controller;

import com.precisionnutrition.model.dto.CustomFoodRequest;
import com.precisionnutrition.model.entity.Food;
import com.precisionnutrition.model.enums.DominantMacro;
import com.precisionnutrition.model.enums.FoodSource;
import com.precisionnutrition.service.FoodService;
import com.precisionnutrition.service.FoodSyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/foods")
@RequiredArgsConstructor
@Slf4j
public class FoodController {

    private final FoodService foodService;
    private final FoodSyncService foodSyncService;

    @GetMapping("/search")
    public ResponseEntity<Page<Food>> searchFoods(
            @RequestParam(name = "q", required = false, defaultValue = "") String query,
            @RequestParam(name = "source", required = false) FoodSource source,
            @RequestParam(name = "dominantMacro", required = false) DominantMacro dominantMacro,
            @RequestParam(name = "includeUsda", required = false, defaultValue = "false") boolean includeUsda,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        String cleanQuery = (query != null) ? query.trim() : "";
        if (includeUsda && !cleanQuery.isBlank()) {
            foodSyncService.searchFoods(cleanQuery);
        }

        PageRequest pageRequest = PageRequest.of(page, size);
        Page<Food> results = foodService.searchFoods(
                cleanQuery,
                source != null ? source.name() : null,
                dominantMacro != null ? dominantMacro.name() : null,
                false,
                pageRequest
        );
        return ResponseEntity.ok(results);
    }

    @GetMapping("/sync-search")
    public ResponseEntity<List<Food>> syncSearchFoods(@RequestParam(name = "q", required = false, defaultValue = "") String query) {
        List<Food> foods = foodSyncService.searchFoods(query);
        return ResponseEntity.ok(foods);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Food> getFoodById(@PathVariable UUID id) {
        Food food = foodService.getFoodById(id);
        return ResponseEntity.ok(food);
    }

    @PostMapping("/custom")
    public ResponseEntity<Food> createCustomFood(@Valid @RequestBody CustomFoodRequest request) {
        Food created = foodService.createCustomFood(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/usda-sync")
    public ResponseEntity<Food> syncUsdaFood(@RequestBody Map<String, String> payload) {
        String fdcId = payload.get("fdcId");
        if (fdcId == null || fdcId.trim().isEmpty()) {
            throw new IllegalArgumentException("O campo 'fdcId' é obrigatório no corpo da requisição.");
        }
        Food synced = foodService.syncUsdaFood(Long.parseLong(fdcId.trim()));
        return ResponseEntity.ok(synced);
    }
}
