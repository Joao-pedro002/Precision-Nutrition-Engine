package com.precisionnutrition.controller;

import com.precisionnutrition.model.dto.WeightHistoryResponse;
import com.precisionnutrition.model.dto.WeightLogEntryDto;
import com.precisionnutrition.model.dto.WeightLogRequest;
import com.precisionnutrition.security.SecurityUtils;
import com.precisionnutrition.service.WeightLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/weight", "/api/v1/weights"})
@RequiredArgsConstructor
public class WeightLogController {

    private final WeightLogService weightLogService;

    @PostMapping
    public ResponseEntity<WeightLogEntryDto> recordWeight(@Valid @RequestBody WeightLogRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        WeightLogRequest scoped = new WeightLogRequest(request.logDate(), request.weightKg(), currentUserId);
        WeightLogEntryDto saved = weightLogService.recordWeight(scoped);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/history")
    public ResponseEntity<WeightHistoryResponse> getWeightHistory() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(weightLogService.getWeightHistory(currentUserId));
    }
}
