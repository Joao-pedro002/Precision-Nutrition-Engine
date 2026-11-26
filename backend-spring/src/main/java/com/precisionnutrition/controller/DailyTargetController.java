package com.precisionnutrition.controller;

import com.precisionnutrition.model.dto.DailyTargetRequest;
import com.precisionnutrition.model.dto.TargetCalculationRequest;
import com.precisionnutrition.model.dto.TargetCalculationResponse;
import com.precisionnutrition.model.entity.DailyTarget;
import com.precisionnutrition.security.SecurityUtils;
import com.precisionnutrition.service.DailyTargetService;
import com.precisionnutrition.service.TargetWizardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/targets")
@RequiredArgsConstructor
@Slf4j
public class DailyTargetController {

    private final DailyTargetService dailyTargetService;
    private final TargetWizardService targetWizardService;

    @PostMapping("/calculate")
    public ResponseEntity<TargetCalculationResponse> calculateTargets(@Valid @RequestBody TargetCalculationRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        TargetCalculationRequest scopedRequest = new TargetCalculationRequest(
                currentUserId,
                request.gender(),
                request.ageYears(),
                request.weightKg(),
                request.heightCm(),
                request.activityLevel(),
                request.goal(),
                request.proteinMultiplier(),
                request.generateBaseMeals()
        );
        return ResponseEntity.ok(targetWizardService.calculateTargets(scopedRequest));
    }

    @PostMapping("/calculate-and-apply")
    public ResponseEntity<DailyTarget> calculateAndApplyTarget(@Valid @RequestBody TargetCalculationRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        TargetCalculationRequest scopedRequest = new TargetCalculationRequest(
                currentUserId,
                request.gender(),
                request.ageYears(),
                request.weightKg(),
                request.heightCm(),
                request.activityLevel(),
                request.goal(),
                request.proteinMultiplier(),
                request.generateBaseMeals()
        );
        DailyTarget applied = targetWizardService.calculateAndApply(scopedRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(applied);
    }

    @GetMapping("/active")
    public ResponseEntity<DailyTarget> getActiveTarget() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        DailyTarget active = dailyTargetService.getActiveTarget(currentUserId);
        return ResponseEntity.ok(active);
    }

    @GetMapping
    public ResponseEntity<List<DailyTarget>> getUserTargets() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        List<DailyTarget> targets = dailyTargetService.getUserTargets(currentUserId);
        return ResponseEntity.ok(targets);
    }

    @PostMapping
    public ResponseEntity<DailyTarget> createTarget(@Valid @RequestBody DailyTargetRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        DailyTargetRequest scopedRequest = new DailyTargetRequest(
                currentUserId,
                request.targetKcalMin(),
                request.targetKcalMax(),
                request.maintenanceKcalCeiling(),
                request.targetProteinG(),
                request.targetCarbohydrateG(),
                request.targetLipidG(),
                request.targetFiberG(),
                request.targetWaterMl(),
                request.targetMicronutrients(),
                request.isActive(),
                request.generateBaseMeals()
        );
        DailyTarget created = dailyTargetService.createTarget(scopedRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<DailyTarget> activateTarget(@PathVariable UUID id) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        DailyTarget activated = dailyTargetService.activateTarget(id, currentUserId);
        return ResponseEntity.ok(activated);
    }

    @PostMapping("/deactivate")
    public ResponseEntity<Void> deactivateTarget() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        dailyTargetService.deactivateActiveTarget(currentUserId);
        return ResponseEntity.noContent().build();
    }
}
