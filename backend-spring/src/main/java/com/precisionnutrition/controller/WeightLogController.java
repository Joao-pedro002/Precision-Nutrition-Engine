package com.precisionnutrition.controller;

import com.precisionnutrition.model.dto.WeightHistoryResponse;
import com.precisionnutrition.model.dto.WeightLogEntryDto;
import com.precisionnutrition.model.dto.WeightLogRequest;
import com.precisionnutrition.service.WeightLogService;
import com.precisionnutrition.util.AppConstants;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/weight")
@RequiredArgsConstructor
public class WeightLogController {

    private final WeightLogService weightLogService;

    @PostMapping
    public ResponseEntity<WeightLogEntryDto> recordWeight(@Valid @RequestBody WeightLogRequest request) {
        WeightLogEntryDto saved = weightLogService.recordWeight(request);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/history")
    public ResponseEntity<WeightHistoryResponse> getWeightHistory(
            @RequestParam(name = "userId", required = false) UUID userId
    ) {
        UUID resolvedUserId = userId != null ? userId : AppConstants.DEFAULT_USER_ID;
        return ResponseEntity.ok(weightLogService.getWeightHistory(resolvedUserId));
    }
}
