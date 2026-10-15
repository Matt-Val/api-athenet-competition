package com.athenet.competition.messaging;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.athenet.competition.config.RabbitConfig;
import com.athenet.competition.model.Competition;
import com.athenet.competition.model.Enrollment;
import com.athenet.competition.model.Match;

import lombok.RequiredArgsConstructor;

/*
    Publica partido.actualizado cuando un partido completo de una competencia ya iniciada
    se crea o cambia. MS-Eventos hace upsert de su copia por matchId.
*/
@Component
@RequiredArgsConstructor
public class MatchUpdatedPublisher {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final RabbitTemplate rabbitTemplate;

    public void publish(Match match, Competition competition, Enrollment home, Enrollment away) {
        MatchUpdatedMessage data = new MatchUpdatedMessage(
                match.getId(),
                match.getCompetitionId(),
                competition.getSportId(),
                match.getRound().getLabel() + ": " + home.getTeamName() + " vs " + away.getTeamName(),
                competition.getName(),
                competition.getCompetitionImg(),
                match.getDate().toString(),
                match.getTime().format(TIME_FORMAT),
                match.getLocation(),
                match.getAddress(),
                match.getHomeTeamId(),
                match.getAwayTeamId(),
                match.getDelegateOid(),
                match.getDelegateDisplayName());

        MatchUpdatedEnvelope envelope = new MatchUpdatedEnvelope(
                UUID.randomUUID().toString(),
                Instant.now().toString(),
                RabbitConfig.MATCH_UPDATED_ROUTING_KEY,
                data);

        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.MATCH_UPDATED_ROUTING_KEY, envelope);
    }
}
