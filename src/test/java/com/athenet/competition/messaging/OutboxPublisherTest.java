package com.athenet.competition.messaging;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import com.athenet.competition.model.OutboxMessage;
import com.athenet.competition.repository.OutboxMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OutboxPublisherTest {
    final OutboxMessageRepository repository = mock(OutboxMessageRepository.class);
    final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    final OutboxPublisher publisher = new OutboxPublisher(repository,rabbit);
    OutboxMessage message() {
        var m = new OutboxMessage();
        m.setId("stable-id"); m.setRoutingKey("resultado.registrado"); m.setAggregateId(7L); m.setRevision(1);
        m.setPayload("{\"messageId\":\"stable-id\"}");
        m.setOccurredAt(Instant.parse("2026-10-18T00:00:00Z")); m.setNextAttemptAt(m.getOccurredAt());
        when(repository.lockNextDue()).thenReturn(Optional.of(m));
        return m;
    }
    @Test void confirmedPersistentMessageIsMarkedPublished() {
        var m = message();
        doAnswer(call -> {
            Message outgoing = call.getArgument(2);
            assertThat(outgoing.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
            assertThat(outgoing.getMessageProperties().getMessageId()).isEqualTo(m.getId());
            assertThat(outgoing.getBody()).isEqualTo(m.getPayload().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            CorrelationData correlation = call.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true,null));
            return null;
        }).when(rabbit).send(eq("athenet.topic"),eq("resultado.registrado"),any(Message.class),any(CorrelationData.class));
        assertThat(publisher.publishNext()).isTrue();
        assertThat(m.getPublishedAt()).isNotNull();
        assertThat(m.getAttempts()).isEqualTo(1);
    }
    @Test void brokerFailureAndUncertainConfirmKeepSamePayloadRecoverable() {
        var m = message();
        doThrow(new org.springframework.amqp.AmqpConnectException(new java.net.ConnectException("offline")))
                .when(rabbit).send(anyString(),anyString(),any(Message.class),any(CorrelationData.class));
        publisher.publishNext();
        assertThat(m.getPublishedAt()).isNull();
        assertThat(m.getNextAttemptAt()).isAfter(Instant.now());
        reset(rabbit);
        doAnswer(call -> {
            CorrelationData correlation = call.getArgument(3);
            correlation.getFuture().completeExceptionally(new TimeoutException("uncertain"));
            return null;
        }).when(rabbit).send(anyString(),anyString(),any(Message.class),any(CorrelationData.class));
        publisher.publishNext();
        assertThat(m.getPublishedAt()).isNull();
        assertThat(m.getAttempts()).isEqualTo(2);
        assertThat(m.getId()).isEqualTo("stable-id");
        assertThat(m.getOccurredAt()).isEqualTo(Instant.parse("2026-10-18T00:00:00Z"));
    }
    @Test void nackAndUnroutableMessagesAreNeverMarkedPublished() {
        var m = message();
        doAnswer(call -> {
            CorrelationData correlation = call.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false,"nack"));
            return null;
        }).when(rabbit).send(anyString(),anyString(),any(Message.class),any(CorrelationData.class));
        publisher.publishNext();
        assertThat(m.getPublishedAt()).isNull();
        reset(rabbit);
        doAnswer(call -> {
            CorrelationData correlation = call.getArgument(3);
            correlation.setReturned(new ReturnedMessage(call.getArgument(2),312,"NO_ROUTE","athenet.topic","resultado.registrado"));
            correlation.getFuture().complete(new CorrelationData.Confirm(true,null));
            return null;
        }).when(rabbit).send(anyString(),anyString(),any(Message.class),any(CorrelationData.class));
        publisher.publishNext();
        assertThat(m.getPublishedAt()).isNull();
    }
    @Test void writerStoresImmutableEnvelopeInsteadOfCallingBroker() {
        var writer = new OutboxEventWriter(repository,new ObjectMapper());
        writer.enqueue("resultado.registrado",7L,1,java.util.Map.of(
                "messageId","stable-id","occurredAt","2026-10-18T00:00:00Z","type","resultado.registrado","data",java.util.Map.of("version",1)));
        var captor = org.mockito.ArgumentCaptor.forClass(OutboxMessage.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo("stable-id");
        assertThat(captor.getValue().getPayload()).contains("occurredAt");
        verifyNoInteractions(rabbit);
    }
}
