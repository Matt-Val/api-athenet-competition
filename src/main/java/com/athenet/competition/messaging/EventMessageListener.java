package com.athenet.competition.messaging;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.athenet.competition.config.RabbitConfig;
import com.athenet.competition.service.MatchService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/* 
    Consume competencia.eventos. No maneja ack/nack a mano: el contenedor está en modo AUTO
    (RabbitConfig), asi que si este método tira una excepción, el interceptor la 
    reintenta hasta 3 veces después del intento inicial, si sigue fallando, el mensaje cae a 
    competencia.eventos.dlq.

*/
@Component
@RequiredArgsConstructor
public class EventMessageListener {

    private static final Logger log = LoggerFactory.getLogger(EventMessageListener.class);

    private final MatchService matchService;
    private final ObjectMapper rabbitObjectMapper;

    @RabbitListener(queues = RabbitConfig.EVENTS_QUEUE)
    public void onEventMessage(Message message) throws IOException {
        EventEnvelope envelope = rabbitObjectMapper.readValue(message.getBody(), EventEnvelope.class);
        EventMessage data = envelope.data();

        switch (envelope.type()) {
            case "evento.creado", "evento.actualizado" -> handleUpsert(data);
            case "evento.cancelado" -> matchService.cancelByEventId(data.eventId());
            default -> log.warn("Tipo de mensaje desconocido en {}: {}",
                    RabbitConfig.EVENTS_QUEUE, envelope.type());
        }
    }

    private void handleUpsert(EventMessage data) {
        if (data.competitionId() == null) {
            matchService.removeIfUnresolved(data.eventId());
            return;
        }

        // Solo nos interesan los eventos type=MATCH. Cualquier otro
        // tipo de evento con competitionId no debería pasar nunca según
        // las validaciones de Eventos, pero lo ignoramos por las dudas.
        if (!"MATCH".equals(data.type())) {
            return;
        }

        matchService.upsertFromEvent(
                data.eventId(),
                data.internalId(),
                data.competitionId(),
                LocalDate.parse(data.eventDate()),
                data.startTime() != null ? LocalTime.parse(data.startTime()) : null,
                data.teamOneId(),
                data.teamTwoId());
    }
}
