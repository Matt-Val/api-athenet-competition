package com.athenet.competition.messaging;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.athenet.competition.config.RabbitConfig;
import com.athenet.competition.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;

class ResultEventPublisherTest {
    @Test
    void publishesEnvelopeWithResultAndRankingData() throws Exception {
        RabbitTemplate template = mock(RabbitTemplate.class);
        Match match = new Match();
        match.setId(31L);
        match.setCompetitionId(4L);
        match.setEventId(57L);
        match.setVersion(2);
        match.setHomeTeamId(12L);
        match.setAwayTeamId(15L);
        match.setHomeScore(2);
        match.setAwayScore(1);
        match.setSimulated(true);
        Competition competition = new Competition();
        competition.setSportId(1L);
        Enrollment home = new Enrollment();
        home.setInstitutionId(3L);
        Enrollment away = new Enrollment();
        away.setInstitutionId(7L);

        new ResultEventPublisher(template).publish(match, competition, home, away);

        var captor = ArgumentCaptor.forClass(ResultRegisteredEnvelope.class);
        verify(template).convertAndSend(eq(RabbitConfig.EXCHANGE),
                eq("resultado.registrado"), captor.capture());
        var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsBytes(captor.getValue()));
        assertEquals("resultado.registrado", json.get("type").asText());
        assertNotNull(json.get("messageId"));
        assertNotNull(json.get("occurredAt"));
        var data = json.get("data");
        assertEquals(31, data.get("matchId").asLong());
        assertEquals(4, data.get("competitionId").asLong());
        assertEquals(1, data.get("sportId").asLong());
        assertEquals(57, data.get("eventId").asLong());
        assertEquals(2, data.get("version").asInt());
        assertEquals(12, data.get("homeTeamId").asLong());
        assertEquals(15, data.get("awayTeamId").asLong());
        assertEquals(3, data.get("homeInstitutionId").asLong());
        assertEquals(7, data.get("awayInstitutionId").asLong());
        assertEquals(2, data.get("homeScore").asInt());
        assertEquals(1, data.get("awayScore").asInt());
        assertTrue(data.get("simulated").asBoolean());
        assertEquals(3, data.get("points").get("win").asInt());
        assertEquals(1, data.get("points").get("draw").asInt());
        assertEquals(0, data.get("points").get("loss").asInt());
    }
}
