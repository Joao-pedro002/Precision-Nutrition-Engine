package com.precisionnutrition.service;

import com.precisionnutrition.repository.DailyLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
public class DailyLogTtlCleanupService {

    public static final int RETENTION_DAYS = 6;

    private final DailyLogRepository dailyLogRepository;

    /**
     * Expulsa fisicamente do banco de dados registros diários com data anterior à data de corte.
     * Devido ao ON DELETE CASCADE na tabela daily_log_entries, todas as entradas filhas
     * também são removidas de forma atômica.
     *
     * @param days Dias de retenção (padrão 6 dias: CURRENT_DATE - 6)
     * @return Quantidade de registros diários deletados
     */
    @Transactional
    public int purgeLogsOlderThanDays(int days) {
        LocalDate cutoffDate = LocalDate.now().minusDays(days);
        log.info("Iniciando expurgo de registros diários (TTL de {} dias). Data de corte: {}", days, cutoffDate);

        int deletedCount = dailyLogRepository.deleteByDateBefore(cutoffDate);
        log.info("Expurgo concluído. {} registros de daily_logs deletados com sucesso (data anterior a {}).",
                deletedCount, cutoffDate);
        return deletedCount;
    }

    /**
     * Execução automática agendada para rodar diariamente à meia-noite.
     */
    @Scheduled(cron = "${app.ttl.cleanup-cron:0 0 0 * * ?}")
    public void purgeDailyLogsAtMidnight() {
        purgeLogsOlderThanDays(RETENTION_DAYS);
    }
}
