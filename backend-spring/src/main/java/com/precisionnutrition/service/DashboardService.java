package com.precisionnutrition.service;

import com.precisionnutrition.model.dto.DashboardResponse;

import java.time.LocalDate;
import java.util.UUID;

public interface DashboardService {

    DashboardResponse getDashboard(UUID userId, LocalDate date);
}
