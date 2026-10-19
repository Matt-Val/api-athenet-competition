package com.athenet.competition.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.messaging.outbox-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxScheduler {
    private final OutboxPublisher publisher;

    @Scheduled(fixedDelayString = "${app.messaging.outbox-delay:2000}")
    public void dispatch() {
        for (int i = 0; i < 50 && !Thread.currentThread().isInterrupted(); i++) {
            try {
                if (!publisher.publishNext()) break;
            } catch (RuntimeException ex) {
                log.warn("Publicación outbox pendiente de recuperación ({})", ex.getClass().getSimpleName());
                break;
            }
        }
    }
}
