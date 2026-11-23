package com.precisionnutrition.service;

import com.precisionnutrition.model.dto.WeightHistoryResponse;
import com.precisionnutrition.model.dto.WeightLogEntryDto;
import com.precisionnutrition.model.dto.WeightLogRequest;

import java.util.UUID;

public interface WeightLogService {

    WeightLogEntryDto recordWeight(WeightLogRequest request);

    WeightHistoryResponse getWeightHistory(UUID userId);
}
