package com.precisionnutrition.controller;

import com.precisionnutrition.model.dto.NutrientAuditResult;
import com.precisionnutrition.model.dto.SubstitutionRequest;
import com.precisionnutrition.model.dto.SubstitutionResult;
import com.precisionnutrition.model.entity.Food;
import com.precisionnutrition.service.NutritionMathService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
