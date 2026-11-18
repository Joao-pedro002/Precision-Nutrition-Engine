package com.precisionnutrition.service;

import com.precisionnutrition.repository.DailyLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DailyLogTtlCleanupServiceTest {

    @Mock
    private DailyLogRepository dailyLogRepository;

    private DailyLogTtlCleanupService cleanupService;

    @BeforeEach
    void setUp() {
        cleanupService = new DailyLogTtlCleanupService(dailyLogRepository);
    }

    @Test
    @DisplayName("Épica 3: Deve expurgar logs com data inferior a CURRENT_DATE - 6 dias")
    void testPurgeLogsOlderThan6Days() {
        LocalDate expectedCutoff = LocalDate.now().minusDays(6);
        when(dailyLogRepository.deleteByDateBefore(expectedCutoff)).thenReturn(12);

        int purged = cleanupService.purgeLogsOlderThanDays(6);

        assertEquals(12, purged);
        ArgumentCaptor<LocalDate> captor = ArgumentCaptor.forClass(LocalDate.class);
        verify(dailyLogRepository, times(1)).deleteByDateBefore(captor.capture());
        assertEquals(expectedCutoff, captor.getValue());
    }

    @Test
    @DisplayName("Épica 3: O agendamento da meia-noite deve executar o purge com 6 dias de retenção")
    void testPurgeAtMidnightScheduled() {
        LocalDate expectedCutoff = LocalDate.now().minusDays(DailyLogTtlCleanupService.RETENTION_DAYS);
        when(dailyLogRepository.deleteByDateBefore(expectedCutoff)).thenReturn(5);

        cleanupService.purgeDailyLogsAtMidnight();

        verify(dailyLogRepository, times(1)).deleteByDateBefore(expectedCutoff);
    }
}
