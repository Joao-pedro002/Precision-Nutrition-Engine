package com.precisionnutrition.controller;

import com.precisionnutrition.model.dto.*;
import com.precisionnutrition.model.entity.DailyLog;
import com.precisionnutrition.model.entity.DailyLogEntry;
import com.precisionnutrition.security.SecurityUtils;
import com.precisionnutrition.service.DailyLogService;
import com.precisionnutrition.service.DailyLogTtlCleanupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
@Slf4j
public class DailyLogController {

    private final DailyLogService dailyLogService;
    private final DailyLogTtlCleanupService ttlCleanupService;

    @GetMapping("/{date}")
    public ResponseEntity<DailyLogResponse> getDailyLog(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        DailyLogResponse response = dailyLogService.getDailyLogResponse(currentUserId, date);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{date}/water")
    public ResponseEntity<WaterUpdateResponse> updateWater(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody WaterUpdateRequest request
    ) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        WaterUpdateRequest scoped = new WaterUpdateRequest(request.mode(), request.amountMl(), currentUserId);
        WaterUpdateResponse response = dailyLogService.updateWater(date, scoped);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{date}/check-meal")
    public ResponseEntity<DailyLog> checkMeal(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody CheckMealRequest request
    ) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        CheckMealRequest scoped = new CheckMealRequest(request.mealId(), request.optionNumber(), currentUserId);
        DailyLog updatedLog = dailyLogService.checkMealOption(date, scoped);
        return ResponseEntity.ok(updatedLog);
    }

    @PostMapping("/{date}/uncheck-meal")
    public ResponseEntity<DailyLog> uncheckMeal(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody CheckMealRequest request
    ) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        CheckMealRequest scoped = new CheckMealRequest(request.mealId(), request.optionNumber(), currentUserId);
        DailyLog updatedLog = dailyLogService.uncheckMealOption(date, scoped);
        return ResponseEntity.ok(updatedLog);
    }

    @PostMapping("/{date}/off-diet")
    public ResponseEntity<DailyLogEntry> addOffDietEntry(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody OffDietEntryRequest request
    ) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        OffDietEntryRequest scoped = new OffDietEntryRequest(
                request.mealId(),
                request.foodId(),
                request.customName(),
                request.weightConsumedG(),
                request.energyKcal(),
                request.proteinG(),
                request.carbohydrateG(),
                request.lipidG(),
                request.fiberG(),
                currentUserId
        );
        DailyLogEntry createdEntry = dailyLogService.addOffDietEntry(date, scoped);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdEntry);
    }

    @DeleteMapping("/entries/{entryId}")
    public ResponseEntity<Void> removeEntry(@PathVariable UUID entryId) {
        dailyLogService.removeEntry(entryId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/purge-old")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> purgeOldLogs(
            @RequestParam(name = "days", required = false, defaultValue = "6") int days
    ) {
        int purged = ttlCleanupService.purgeLogsOlderThanDays(days);
        LocalDate cutoffDate = LocalDate.now().minusDays(days);
        return ResponseEntity.ok(Map.of(
                "purgedCount", purged,
                "cutoffDate", cutoffDate.toString(),
                "message", "Expurgo de registros com TTL de " + days + " dias concluído com sucesso."
        ));
    }
}
