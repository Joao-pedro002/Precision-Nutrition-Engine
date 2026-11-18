package com.precisionnutrition.service;

import com.precisionnutrition.model.dto.CheckMealRequest;
import com.precisionnutrition.model.dto.DailyLogResponse;
import com.precisionnutrition.model.dto.OffDietEntryRequest;
import com.precisionnutrition.model.dto.WaterUpdateRequest;
import com.precisionnutrition.model.dto.WaterUpdateResponse;
import com.precisionnutrition.model.entity.DailyLog;
import com.precisionnutrition.model.entity.DailyLogEntry;

import java.time.LocalDate;
import java.util.UUID;

public interface DailyLogService {

    DailyLog getOrCreateDailyLog(UUID userId, LocalDate date);

    DailyLogResponse getDailyLogResponse(UUID userId, LocalDate date);

    WaterUpdateResponse updateWater(LocalDate date, WaterUpdateRequest request);

    DailyLog checkMealOption(LocalDate date, CheckMealRequest request);

    DailyLog uncheckMealOption(LocalDate date, CheckMealRequest request);

    DailyLogEntry addOffDietEntry(LocalDate date, OffDietEntryRequest request);

    void removeEntry(UUID entryId);
}
