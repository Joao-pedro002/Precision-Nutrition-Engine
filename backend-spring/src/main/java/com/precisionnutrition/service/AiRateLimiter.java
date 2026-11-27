package com.precisionnutrition.service;

import com.precisionnutrition.exception.RateLimitExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AiRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final int maxRequestsPerMinute;
    private final ConcurrentHashMap<UUID, Deque<Instant>> requestTimestampsByUser = new ConcurrentHashMap<>();

    public AiRateLimiter(
            @Value("${app.ai.gemini.rate-limit-per-minute:10}") int maxRequestsPerMinute
    ) {
        this.maxRequestsPerMinute = Math.max(1, maxRequestsPerMinute);
    }

    public void checkAndConsume(UUID userId) {
        Instant now = Instant.now();
        Instant windowStart = now.minus(WINDOW);

        Deque<Instant> timestamps = requestTimestampsByUser.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxRequestsPerMinute) {
                throw new RateLimitExceededException(
                        String.format(
                                "Você atingiu o limite de %d perguntas por minuto. Aguarde alguns instantes e tente novamente.",
                                maxRequestsPerMinute
                        )
                );
            }
            timestamps.addLast(now);
        }
    }

    public void resetForUser(UUID userId) {
        requestTimestampsByUser.remove(userId);
    }

    public void resetAll() {
        requestTimestampsByUser.clear();
    }
}
