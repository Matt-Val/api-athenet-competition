package com.athenet.competition.messaging;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import com.athenet.competition.service.MatchService;
import com.fasterxml.jackson.databind.ObjectMapper;

class EventMessageListenerTest {
    final MatchService matches = mock(MatchService.class);
    final EventMessageListener listener = new EventMessageListener(matches, new ObjectMapper());

    Message message(String type, String competitionId) {
        String json = """
            {"messageId":"test","occurredAt":"2026-10-12T00:00:00Z","type":"%s",
             "data":{"eventId":57,"internalId":"EVT-TEST-01","type":"MATCH","status":"PUBLISHED",
             "competitionId":%s,"eventDate":"2026-11-08","startTime":"18:30","teamOneId":1,"teamTwoId":2}}
            """.formatted(type, competitionId);
        MessageProperties properties = new MessageProperties();
        properties.setHeader("__TypeId__", "com.other.service.EventEnvelope");
        return new Message(json.getBytes(StandardCharsets.UTF_8), properties);
    }

    @Test void foreignTypeHeaderDoesNotPreventConsumption() throws Exception {
        listener.onEventMessage(message("evento.creado", "1"));
        verify(matches).upsertFromEvent(57L, "EVT-TEST-01", 1L,
                LocalDate.of(2026, 11, 8), LocalTime.of(18, 30), 1L, 2L);
    }

    @Test void cancellationIsForwarded() throws Exception {
        listener.onEventMessage(message("evento.cancelado", "1"));
        verify(matches).cancelByEventId(57L);
    }

    @Test void removedCompetitionRemovesUnresolvedMatch() throws Exception {
        listener.onEventMessage(message("evento.actualizado", "null"));
        verify(matches).removeIfUnresolved(57L);
    }

    @Test void malformedJsonPropagatesForRetry() {
        assertThrows(java.io.IOException.class, () -> listener.onEventMessage(
                new Message("invalid".getBytes(StandardCharsets.UTF_8), new MessageProperties())));
        verifyNoInteractions(matches);
    }
}
