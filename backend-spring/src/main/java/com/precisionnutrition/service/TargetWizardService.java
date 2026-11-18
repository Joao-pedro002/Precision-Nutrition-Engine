package com.precisionnutrition.service;

import com.precisionnutrition.model.dto.TargetCalculationRequest;
import com.precisionnutrition.model.dto.TargetCalculationResponse;
import com.precisionnutrition.model.entity.DailyTarget;

public interface TargetWizardService {

    TargetCalculationResponse calculateTargets(TargetCalculationRequest request);

    DailyTarget calculateAndApply(TargetCalculationRequest request);
}
