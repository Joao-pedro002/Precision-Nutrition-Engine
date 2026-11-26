package com.precisionnutrition.controller;

import com.precisionnutrition.model.dto.DashboardResponse;
import com.precisionnutrition.security.SecurityUtils;
import com.precisionnutrition.service.DashboardService;
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
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        LocalDate resolvedDate = date != null ? date : LocalDate.now();

        log.info("Carregando dashboard nutricional para usuário {} na data {}", currentUserId, resolvedDate);
        DashboardResponse response = dashboardService.getDashboard(currentUserId, resolvedDate);
        return ResponseEntity.ok(response);
    }
}
