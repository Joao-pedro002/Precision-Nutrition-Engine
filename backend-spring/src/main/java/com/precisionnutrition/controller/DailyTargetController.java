package com.precisionnutrition.controller;

import com.precisionnutrition.model.dto.DailyTargetRequest;
import com.precisionnutrition.model.entity.DailyTarget;
import com.precisionnutrition.service.DailyTargetService;
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
}
