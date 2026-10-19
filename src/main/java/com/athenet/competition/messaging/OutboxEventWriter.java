package com.athenet.competition.messaging;

import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.athenet.competition.model.OutboxMessage;
import com.athenet.competition.repository.OutboxMessageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OutboxEventWriter {
    private final OutboxMessageRepository messages;
    private final ObjectMapper rabbitObjectMapper;

    /** Debe participar en la misma transacción que el resultado o la agenda. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String routingKey, Long aggregateId, int revision, Object envelope) {
        try {
            String payload = rabbitObjectMapper.writeValueAsString(envelope);
            var tree = rabbitObjectMapper.readTree(payload);
            var message = new OutboxMessage();
            message.setId(tree.path("messageId").asText());
            message.setOccurredAt(Instant.parse(tree.path("occurredAt").asText()));
            message.setRoutingKey(routingKey);
            message.setAggregateId(aggregateId);
            message.setRevision(revision);
            message.setPayload(payload);
            message.setNextAttemptAt(message.getOccurredAt());
            messages.save(message);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo serializar el evento de dominio", ex);
        }
    }
}
