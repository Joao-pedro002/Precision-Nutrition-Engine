package com.precisionnutrition.controller;

import com.precisionnutrition.model.dto.DashboardResponse;
import com.precisionnutrition.service.DashboardService;
import com.precisionnutrition.util.AppConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public ResponseEntity<DashboardResponse> getDashboard(
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "userId", required = false) UUID userId
    ) {
        UUID resolvedUserId = userId != null ? userId : AppConstants.DEFAULT_USER_ID;
        LocalDate resolvedDate = date != null ? date : LocalDate.now();

        log.info("Carregando dashboard nutricional para usuário {} na data {}", resolvedUserId, resolvedDate);
        DashboardResponse response = dashboardService.getDashboard(resolvedUserId, resolvedDate);
        return ResponseEntity.ok(response);
    }
}
