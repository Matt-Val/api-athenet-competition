package com.athenet.competition.messaging;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.athenet.competition.config.RabbitConfig;
import com.athenet.competition.model.Competition;
import com.athenet.competition.model.Enrollment;
import com.athenet.competition.model.Match;

import lombok.RequiredArgsConstructor;

/* 
    Publica resultado.registrado cada vez que el delegado guarda un resultado
    nuevo o una corrección (MatchService.registerResult).
*/
@Component
@RequiredArgsConstructor
public class ResultEventPublisher {

    private final OutboxEventWriter sender;

    public void publish(Match match, Competition competition, Enrollment home, Enrollment away) {
        ResultRegisteredMessage data = new ResultRegisteredMessage(
                match.getId(),
                match.getCompetitionId(),
                competition.getName(),
                competition.getSportId(),
                match.getRound().getLabel(),
                match.getVersion(),
                match.getHomeTeamId(),
                match.getAwayTeamId(),
                home.getTeamName(),
                away.getTeamName(),
                home.getInstitutionId(),
                away.getInstitutionId(),
                match.getHomeScore(),
                match.getAwayScore(),
                new ResultRegisteredMessage.Points(
                        competition.getWinPoints(), competition.getDrawPoints(), competition.getLossPoints()),
                match.getDate() == null ? null : match.getDate().toString());

        ResultRegisteredEnvelope envelope = new ResultRegisteredEnvelope(
                UUID.randomUUID().toString(),
                Instant.now().toString(),
                RabbitConfig.RESULT_REGISTERED_ROUTING_KEY,
                data);

        sender.enqueue(RabbitConfig.RESULT_REGISTERED_ROUTING_KEY, match.getId(), match.getVersion(), envelope);
    }
}
