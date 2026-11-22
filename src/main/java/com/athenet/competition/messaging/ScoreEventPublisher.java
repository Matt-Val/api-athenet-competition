package com.athenet.competition.messaging;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.athenet.competition.exception.UpstreamServiceException;

import lombok.extern.slf4j.Slf4j;

/*
    Publica eventos del marcador en vivo en Kafka (topic "marcador", key = idEncuentro).
    - PUNTO: síncrono con timeout; si Kafka no confirma, responde 503 (UpstreamServiceException).
    - INICIADO / FINALIZADO: después del commit de BD; un fallo de Kafka no revierte ni rompe
      la operación de dominio, sino que se registra y reintenta de forma acotada.
*/
@Slf4j
@Component
public class ScoreEventPublisher implements DisposableBean {

    private static final int MAX_AFTER_COMMIT_ATTEMPTS = 3;
    private static final long DEFAULT_RETRY_BASE_DELAY_MS = 1_000L;

    private final KafkaTemplate<String, ScoreEvent> kafkaTemplate;
    private final String topic;
    private final Duration sendTimeout;
    private final ScheduledExecutorService retryExecutor;
    private final long retryBaseDelayMs;

    @Autowired
    public ScoreEventPublisher(
            KafkaTemplate<String, ScoreEvent> kafkaTemplate,
            @Value("${app.kafka.topic.marcador:marcador}") String topic,
            @Value("${app.kafka.send-timeout:3s}") Duration sendTimeout) {
        this(kafkaTemplate, topic, sendTimeout, newSingleThreadScheduler(), DEFAULT_RETRY_BASE_DELAY_MS);
    }

    ScoreEventPublisher(
            KafkaTemplate<String, ScoreEvent> kafkaTemplate,
            String topic,
            Duration sendTimeout,
            ScheduledExecutorService retryExecutor,
            long retryBaseDelayMs) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.sendTimeout = sendTimeout;
        this.retryExecutor = retryExecutor;
        this.retryBaseDelayMs = retryBaseDelayMs;
    }

    private static ScheduledExecutorService newSingleThreadScheduler() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "kafka-score-publisher");
            thread.setDaemon(true);
            return thread;
        });
    }

    // Envío síncrono para puntos en vivo: requiere confirmación del broker dentro del timeout.
    public void publishPoint(ScoreEvent event) {
        try {
            sendAndAwait(event);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrumpido al publicar marcador en vivo del partido {}", event.idEncuentro(), ex);
            throw new UpstreamServiceException("No se pudo publicar el marcador en vivo. Intenta nuevamente.", ex);
        } catch (Exception ex) {
            log.warn("Kafka no confirmó marcador en vivo del partido {}: {}", event.idEncuentro(), ex.getMessage());
            throw new UpstreamServiceException("No se pudo publicar el marcador en vivo. Intenta nuevamente.", ex);
        }
    }

    /*
        Publica INICIADO o FINALIZADO tras confirmar la transacción de BD.
        Nunca propaga excepciones al llamador para no afectar el flujo principal.
    */
    public void publishAfterCommit(ScoreEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatchWithRetries(event);
                }
            });
            return;
        }
        dispatchWithRetries(event);
    }

    private void dispatchWithRetries(ScoreEvent event) {
        try {
            retryExecutor.execute(() -> attemptSend(event, 1));
        } catch (Exception ex) {
            log.error("No se pudo agendar publicación {} del partido {} en Kafka: {}",
                    event.tipo(), event.idEncuentro(), ex.getMessage());
        }
    }

    private void attemptSend(ScoreEvent event, int attempt) {
        try {
            sendAndAwait(event);
            log.debug("Evento {} publicado en Kafka para partido {} ({}-{})",
                    event.tipo(), event.idEncuentro(), event.puntosLocal(), event.puntosVisita());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Interrumpido al publicar evento {} del partido {} (intento {}/{})",
                    event.tipo(), event.idEncuentro(), attempt, MAX_AFTER_COMMIT_ATTEMPTS, ex);
        } catch (Exception ex) {
            if (attempt < MAX_AFTER_COMMIT_ATTEMPTS) {
                long delayMs = retryBaseDelayMs * (1L << (attempt - 1));
                log.warn("Fallo al publicar {} del partido {} en Kafka (intento {}/{}): {}. Reintentando en {} ms",
                        event.tipo(), event.idEncuentro(), attempt, MAX_AFTER_COMMIT_ATTEMPTS,
                        ex.getMessage(), delayMs);
                retryExecutor.schedule(() -> attemptSend(event, attempt + 1), delayMs, TimeUnit.MILLISECONDS);
            } else {
                log.error("No se pudo publicar {} del partido {} en Kafka tras {} intentos: {}",
                        event.tipo(), event.idEncuentro(), MAX_AFTER_COMMIT_ATTEMPTS, ex.getMessage());
            }
        }
    }

    private void sendAndAwait(ScoreEvent event) throws Exception {
        String key = String.valueOf(event.idEncuentro());
        kafkaTemplate.send(topic, key, event).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void destroy() {
        retryExecutor.shutdownNow();
    }
}
