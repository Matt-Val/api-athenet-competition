package com.athenet.competition.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.athenet.competition.config.RabbitConfig;
import com.athenet.competition.repository.OutboxMessageRepository;

@lombok.extern.slf4j.Slf4j
@Service
public class OutboxPublisher {
    private final OutboxMessageRepository messages;
    private final RabbitTemplate rabbit;

    public OutboxPublisher(OutboxMessageRepository messages, @Qualifier("outboxRabbitTemplate") RabbitTemplate rabbit) {
        this.messages = messages; this.rabbit = rabbit;
    }

    /** Un lock por mensaje hasta resolver confirm/return; no bloquea la transacción del delegado. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean publishNext() {
        var message = messages.lockNextDue().orElse(null);
        if (message == null) return false;
        message.setAttempts(message.getAttempts() + 1);
        try {
            var properties = new MessageProperties();
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            properties.setContentEncoding(StandardCharsets.UTF_8.name());
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            properties.setMessageId(message.getId());
            var correlation = new CorrelationData(message.getId());
            rabbit.send(RabbitConfig.EXCHANGE, message.getRoutingKey(),
                    new Message(message.getPayload().getBytes(StandardCharsets.UTF_8), properties), correlation);
            var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
            if (!confirm.isAck() || correlation.getReturned() != null) {
                throw new IllegalStateException("BROKER_NACK_OR_RETURN");
            }
            message.setPublishedAt(Instant.now());
            message.setLastError(null);
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            message.setLastError(ex.getClass().getSimpleName());
            log.warn("Publicación pendiente {} ({}, revisión {}, fallo {})", message.getId(),
                    message.getRoutingKey(), message.getRevision(), ex.getClass().getSimpleName());
            message.setNextAttemptAt(Instant.now().plusSeconds(5L << Math.min(message.getAttempts() - 1, 6)));
        }
        return true;
    }
}
