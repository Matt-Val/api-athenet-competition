package com.athenet.competition.messaging;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.athenet.competition.config.RabbitConfig;

class AfterCommitSenderTest {

    private final RabbitTemplate template = mock(RabbitTemplate.class);
    private final AfterCommitSender sender = new AfterCommitSender(template);

    @AfterEach
    void clearTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void sendsOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();

        sender.send("resultado.registrado", "envelope");
        verifyNoInteractions(template);

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(template).convertAndSend(RabbitConfig.EXCHANGE, "resultado.registrado", "envelope");
    }

    @Test
    void rollbackDoesNotSend() {
        TransactionSynchronizationManager.initSynchronization();

        sender.send("resultado.registrado", "envelope");
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verifyNoInteractions(template);
    }

    @Test
    void sendsRightAwayWithoutTransaction() {
        sender.send("resultado.registrado", "envelope");

        verify(template).convertAndSend(RabbitConfig.EXCHANGE, "resultado.registrado", "envelope");
    }

    @Test
    void brokerFailureAfterCommitDoesNotFailTheRequest() {
        doThrow(new AmqpConnectException(new RuntimeException("broker caído")))
                .when(template).convertAndSend(any(String.class), any(String.class), any(Object.class));
        TransactionSynchronizationManager.initSynchronization();

        sender.send("resultado.registrado", "envelope");

        assertDoesNotThrow(() -> TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit));
    }
}
