package com.precisionnutrition.controller;

import com.precisionnutrition.model.dto.DailyTargetRequest;
import com.precisionnutrition.model.dto.TargetCalculationRequest;
import com.precisionnutrition.model.dto.TargetCalculationResponse;
import com.precisionnutrition.model.entity.DailyTarget;
import com.precisionnutrition.service.DailyTargetService;
import com.precisionnutrition.service.TargetWizardService;
import com.precisionnutrition.util.AppConstants;
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
        TargetCalculationResponse response = targetWizardService.calculateTargets(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/calculate-and-apply")
    public ResponseEntity<DailyTarget> calculateAndApplyTarget(@Valid @RequestBody TargetCalculationRequest request) {
        DailyTarget applied = targetWizardService.calculateAndApply(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(applied);
    }

    @GetMapping("/active")
    public ResponseEntity<DailyTarget> getActiveTarget(@RequestParam(name = "userId", required = false) UUID userId) {
        UUID resolvedUserId = userId != null ? userId : AppConstants.DEFAULT_USER_ID;
        DailyTarget active = dailyTargetService.getActiveTarget(resolvedUserId);
        return ResponseEntity.ok(active);
    }

    @GetMapping
    public ResponseEntity<List<DailyTarget>> getUserTargets(@RequestParam(name = "userId", required = false) UUID userId) {
        UUID resolvedUserId = userId != null ? userId : AppConstants.DEFAULT_USER_ID;
        List<DailyTarget> targets = dailyTargetService.getUserTargets(resolvedUserId);
        return ResponseEntity.ok(targets);
    }

    @PostMapping
    public ResponseEntity<DailyTarget> createTarget(@Valid @RequestBody DailyTargetRequest request) {
        DailyTarget created = dailyTargetService.createTarget(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<DailyTarget> activateTarget(
            @PathVariable UUID id,
            @RequestParam(name = "userId", required = false) UUID userId
    ) {
        UUID resolvedUserId = userId != null ? userId : AppConstants.DEFAULT_USER_ID;
        DailyTarget activated = dailyTargetService.activateTarget(id, resolvedUserId);
        return ResponseEntity.ok(activated);
    }

    @PostMapping("/deactivate")
    public ResponseEntity<Void> deactivateTarget(@RequestParam(name = "userId", required = false) UUID userId) {
        UUID resolvedUserId = userId != null ? userId : AppConstants.DEFAULT_USER_ID;
        dailyTargetService.deactivateActiveTarget(resolvedUserId);
        return ResponseEntity.noContent().build();
    }
}
