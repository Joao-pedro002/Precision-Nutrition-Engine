package com.precisionnutrition.controller;

import com.precisionnutrition.model.dto.*;
import com.precisionnutrition.model.entity.DailyLog;
import com.precisionnutrition.model.entity.DailyLogEntry;
import com.precisionnutrition.service.DailyLogService;
import com.precisionnutrition.util.AppConstants;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
@Slf4j
public class DailyLogController {

    private final DailyLogService dailyLogService;

    @GetMapping("/{date}")
    public ResponseEntity<DailyLogResponse> getDailyLog(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "userId", required = false) UUID userId
    ) {
        UUID resolvedUserId = userId != null ? userId : AppConstants.DEFAULT_USER_ID;
        DailyLogResponse response = dailyLogService.getDailyLogResponse(resolvedUserId, date);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{date}/water")
    public ResponseEntity<WaterUpdateResponse> updateWater(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody WaterUpdateRequest request
    ) {
        WaterUpdateResponse response = dailyLogService.updateWater(date, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{date}/check-meal")
    public ResponseEntity<DailyLog> checkMeal(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody CheckMealRequest request
    ) {
        DailyLog updatedLog = dailyLogService.checkMealOption(date, request);
        return ResponseEntity.ok(updatedLog);
    }

    @PostMapping("/{date}/off-diet")
    public ResponseEntity<DailyLogEntry> addOffDietEntry(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody OffDietEntryRequest request
    ) {
        DailyLogEntry createdEntry = dailyLogService.addOffDietEntry(date, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdEntry);
    }

    @DeleteMapping("/entries/{entryId}")
    public ResponseEntity<Void> removeEntry(@PathVariable UUID entryId) {
        dailyLogService.removeEntry(entryId);
        return ResponseEntity.noContent().build();
    }
}
