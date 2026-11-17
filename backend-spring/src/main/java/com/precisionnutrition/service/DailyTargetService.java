package com.precisionnutrition.service;

import com.precisionnutrition.model.dto.DailyTargetRequest;
import com.precisionnutrition.model.entity.DailyTarget;

import java.util.List;
import java.util.UUID;

public interface DailyTargetService {

    DailyTarget getActiveTarget(UUID userId);

    List<DailyTarget> getUserTargets(UUID userId);

    DailyTarget createTarget(DailyTargetRequest request);

    DailyTarget activateTarget(UUID targetId, UUID userId);
}
