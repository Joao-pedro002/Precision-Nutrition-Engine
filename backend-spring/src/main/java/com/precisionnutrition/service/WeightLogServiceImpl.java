package com.precisionnutrition.service;

import com.precisionnutrition.model.dto.WeightHistoryResponse;
import com.precisionnutrition.model.dto.WeightLogEntryDto;
import com.precisionnutrition.model.dto.WeightLogRequest;
import com.precisionnutrition.model.entity.WeightLog;
import com.precisionnutrition.repository.WeightLogRepository;
import com.precisionnutrition.util.AppConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WeightLogServiceImpl implements WeightLogService {

    private final WeightLogRepository weightLogRepository;

    @Override
    @Transactional
    public WeightLogEntryDto recordWeight(WeightLogRequest request) {
        UUID resolvedUserId = request.userId() != null ? request.userId() : AppConstants.DEFAULT_USER_ID;
        LocalDate resolvedDate = request.logDate() != null ? request.logDate() : LocalDate.now();
        BigDecimal normalizedWeight = request.weightKg().setScale(2, RoundingMode.HALF_UP);

        WeightLog entity = weightLogRepository.findByUserIdAndLogDate(resolvedUserId, resolvedDate)
                .map(existing -> {
                    existing.setWeightKg(normalizedWeight);
                    return existing;
                })
                .orElseGet(() -> WeightLog.builder()
                        .userId(resolvedUserId)
                        .logDate(resolvedDate)
                        .weightKg(normalizedWeight)
                        .build());

        WeightLog saved = weightLogRepository.save(entity);
        log.info("Peso corporal registrado/atualizado para {} em {}: {} kg", resolvedUserId, resolvedDate, normalizedWeight);

        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public WeightHistoryResponse getWeightHistory(UUID userId) {
        UUID resolvedUserId = userId != null ? userId : AppConstants.DEFAULT_USER_ID;
        List<WeightLog> logs = weightLogRepository.findTop30ByUserIdOrderByLogDateDesc(resolvedUserId);

        List<WeightLogEntryDto> history = logs.stream()
                .map(this::toDto)
                .toList();

        if (logs.isEmpty()) {
            return new WeightHistoryResponse(null, null, null, null, history);
        }

        WeightLog latest = logs.get(0);
        BigDecimal latestWeight = latest.getWeightKg();
        LocalDate latestDate = latest.getLogDate();

        BigDecimal previousWeight = logs.size() >= 2 ? logs.get(1).getWeightKg() : null;
        BigDecimal deltaKg = (previousWeight != null)
                ? latestWeight.subtract(previousWeight).setScale(2, RoundingMode.HALF_UP)
                : null;

        return new WeightHistoryResponse(latestWeight, latestDate, previousWeight, deltaKg, history);
    }

    private WeightLogEntryDto toDto(WeightLog entity) {
        return new WeightLogEntryDto(
                entity.getId(),
                entity.getLogDate(),
                entity.getWeightKg(),
                entity.getCreatedAt()
        );
    }
}
