package com.precisionnutrition.controller;

import com.precisionnutrition.model.dto.NutrientAuditResult;
import com.precisionnutrition.model.dto.QuickSubstitutionDto;
import com.precisionnutrition.model.dto.SubstitutionRequest;
import com.precisionnutrition.model.dto.SubstitutionResult;
import com.precisionnutrition.model.entity.Food;
import com.precisionnutrition.model.enums.MacroTarget;
import com.precisionnutrition.service.NutritionMathService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/nutrition")
@RequiredArgsConstructor
@Slf4j
public class NutritionMathController {

    private final NutritionMathService nutritionMathService;

    @PostMapping("/substitute")
    public ResponseEntity<SubstitutionResult> calculateSubstitution(@Valid @RequestBody SubstitutionRequest request) {
        log.info("Calculando substituição de alimento: sourceId={}, weight={}, targetId={}",
                request.sourceFoodId(), request.sourceWeightG(), request.targetFoodId());

        SubstitutionResult result = nutritionMathService.calculateSubstitution(
                request.sourceFoodId(),
                request.sourceWeightG(),
                request.targetFoodId(),
                request.targetMacroOverride()
        );
        return ResponseEntity.ok(result);
    }

    @PostMapping("/audit-atwater")
    public ResponseEntity<NutrientAuditResult> auditAtwater(@Valid @RequestBody Food food) {
        NutrientAuditResult auditResult = nutritionMathService.auditAtwaterEnergy(food);
        return ResponseEntity.ok(auditResult);
    }

    @GetMapping("/quick-substitutions")
    public ResponseEntity<List<QuickSubstitutionDto>> getQuickSubstitutions(
            @RequestParam("foodId") UUID foodId,
            @RequestParam(name = "weightG", required = false, defaultValue = "100.00") BigDecimal weightG,
            @RequestParam(name = "targetMacroOverride", required = false) MacroTarget targetMacroOverride,
            @RequestParam(name = "limit", required = false, defaultValue = "5") int limit
    ) {
        List<QuickSubstitutionDto> suggestions = nutritionMathService.getQuickSubstitutions(
                foodId, weightG, targetMacroOverride, limit
        );
        return ResponseEntity.ok(suggestions);
    }
}
