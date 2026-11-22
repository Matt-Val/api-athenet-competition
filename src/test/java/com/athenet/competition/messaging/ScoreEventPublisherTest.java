package com.athenet.competition.messaging;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.athenet.competition.exception.UpstreamServiceException;

@ExtendWith(MockitoExtension.class)
class ScoreEventPublisherTest {

    @Mock KafkaTemplate<String, ScoreEvent> kafkaTemplate;
    @Mock ScheduledExecutorService executor;
    @Mock SendResult<String, ScoreEvent> sendResult;

    ScoreEventPublisher publisher;
    ScoreEvent pointEvent;

    @BeforeEach
    void setup() {
        publisher = new ScoreEventPublisher(kafkaTemplate, "marcador", Duration.ofMillis(200), executor, 10L);
        pointEvent = new ScoreEvent(31L, "USACH", "U. de Chile", 2, 1, ScoreEvent.TIPO_PUNTO);
    }

    @AfterEach
    void cleanupTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void publishPointSendsWithMatchIdAsKey() {
        when(kafkaTemplate.send("marcador", "31", pointEvent))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        publisher.publishPoint(pointEvent);

        verify(kafkaTemplate).send("marcador", "31", pointEvent);
    }

    @Test
    void publishPointThrowsUpstreamServiceExceptionWhenKafkaFails() {
        CompletableFuture<SendResult<String, ScoreEvent>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker caído"));
        when(kafkaTemplate.send("marcador", "31", pointEvent)).thenReturn(failed);

        assertThrows(UpstreamServiceException.class, () -> publisher.publishPoint(pointEvent));
    }

    @Test
    void publishPointThrowsUpstreamServiceExceptionOnTimeout() {
        when(kafkaTemplate.send("marcador", "31", pointEvent)).thenReturn(new CompletableFuture<>());

        assertThrows(UpstreamServiceException.class, () -> publisher.publishPoint(pointEvent));
    }

    @Test
    void publishAfterCommitWaitsUntilTransactionCommits() {
        ScoreEvent started = new ScoreEvent(31L, "USACH", "U. de Chile", 0, 0, ScoreEvent.TIPO_INICIADO);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(executor).execute(any(Runnable.class));
        when(kafkaTemplate.send("marcador", "31", started))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        publisher.publishAfterCommit(started);
        verifyNoInteractions(kafkaTemplate);

        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
        verify(kafkaTemplate).send("marcador", "31", started);
    }

    @Test
    void publishAfterCommitRetriesBoundedTimesWithoutThrowing() {
        ScoreEvent finalized = new ScoreEvent(31L, "USACH", "U. de Chile", 3, 2, ScoreEvent.TIPO_FINALIZADO);
        CompletableFuture<SendResult<String, ScoreEvent>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker caído"));
        when(kafkaTemplate.send("marcador", "31", finalized)).thenReturn(failed);

        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(executor).execute(any(Runnable.class));
        when(executor.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS))).thenAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        });

        assertDoesNotThrow(() -> publisher.publishAfterCommit(finalized));
        verify(kafkaTemplate, times(3)).send("marcador", "31", finalized);
    }

    @Test
    void matchTeamNamesCachesUntilTtlExpires() {
        Instant[] now = {Instant.parse("2026-11-22T12:00:00Z")};
        Clock mutableClock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now[0]; }
        };
        MatchTeamNames cache = new MatchTeamNames(Duration.ofMinutes(5), mutableClock);
        AtomicInteger loads = new AtomicInteger();

        MatchTeamNames.Names first = cache.getOrLoad(31L, () -> {
            loads.incrementAndGet();
            return new MatchTeamNames.Names("USACH", "U. de Chile");
        });
        MatchTeamNames.Names second = cache.getOrLoad(31L, () -> {
            loads.incrementAndGet();
            return new MatchTeamNames.Names("Otro", "Otro");
        });
        assertEquals(first, second);
        assertEquals(1, loads.get());

        now[0] = now[0].plus(Duration.ofMinutes(6));
        cache.getOrLoad(31L, () -> {
            loads.incrementAndGet();
            return new MatchTeamNames.Names("USACH", "U. de Chile");
        });
        assertEquals(2, loads.get());
    }
}
