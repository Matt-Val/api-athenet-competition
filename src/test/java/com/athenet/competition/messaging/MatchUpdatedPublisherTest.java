package com.athenet.competition.messaging;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.athenet.competition.config.RabbitConfig;
import com.athenet.competition.model.*;
import com.athenet.competition.model.enums.MatchRound;
import com.fasterxml.jackson.databind.ObjectMapper;

class MatchUpdatedPublisherTest {
    @Test
    void publishesEnvelopeWithEventsCopyData() throws Exception {
        RabbitTemplate template = mock(RabbitTemplate.class);
        Match match = new Match();
        match.setId(31L);
        match.setCompetitionId(4L);
        match.setRound(MatchRound.ROUND_OF_16);
        match.setPosition(3);
        match.setDate(LocalDate.of(2026, 11, 8));
        match.setTime(LocalTime.of(18, 0));
        match.setLocation("Santiago, Chile");
        match.setAddress("Av. Grecia 2001");
        match.setHomeTeamId(12L);
        match.setAwayTeamId(15L);
        match.setDelegateOid("3f2b9c1e-5a7d-4c1b-9e2f-1d0a8b6c4e21");
        match.setDelegateDisplayName("Delegado de prueba");
        Competition competition = new Competition();
        competition.setName("Copa Instituciones de Chile 2026");
        competition.setSportId(1L);
        competition.setCompetitionImg("https://ejemplo.com/copa.png");
        Enrollment home = new Enrollment();
        home.setTeamName("Halcones");
        Enrollment away = new Enrollment();
        away.setTeamName("Pumas");

        new MatchUpdatedPublisher(new AfterCommitSender(template)).publish(match, competition, home, away);

        var captor = ArgumentCaptor.forClass(MatchUpdatedEnvelope.class);
        verify(template).convertAndSend(eq(RabbitConfig.EXCHANGE),
                eq("partido.actualizado"), captor.capture());
        var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsBytes(captor.getValue()));
        assertEquals("partido.actualizado", json.get("type").asText());
        assertNotNull(json.get("messageId"));
        var data = json.get("data");
        assertEquals(31, data.get("matchId").asLong());
        assertEquals(4, data.get("competitionId").asLong());
        assertEquals(1, data.get("sportId").asLong());
        assertEquals("Octavos de final: Halcones vs Pumas", data.get("title").asText());
        assertEquals("Copa Instituciones de Chile 2026", data.get("description").asText());
        assertEquals("https://ejemplo.com/copa.png", data.get("competitionImg").asText());
        assertEquals("2026-11-08", data.get("date").asText());
        assertEquals("18:00", data.get("time").asText());
        assertEquals("Santiago, Chile", data.get("location").asText());
        assertEquals(12, data.get("homeTeamId").asLong());
        assertEquals(15, data.get("awayTeamId").asLong());
        assertEquals("3f2b9c1e-5a7d-4c1b-9e2f-1d0a8b6c4e21", data.get("delegateOid").asText());
        assertEquals("Delegado de prueba", data.get("delegateDisplayName").asText());
    }
}
