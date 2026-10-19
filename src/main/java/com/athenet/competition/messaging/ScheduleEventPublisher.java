package com.athenet.competition.messaging;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import com.athenet.competition.model.*;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ScheduleEventPublisher {
    public static final String ROUTING_KEY = "partido.actualizado";
    private final OutboxEventWriter outbox;
    private final com.athenet.competition.repository.OutboxMessageRepository messages;
    private final com.fasterxml.jackson.databind.ObjectMapper rabbitObjectMapper;

    public record Data(Long matchId, Long competitionId, String competitionName, Long sportId,
            int scheduleVersion, String title, String description, String coverImage,
            String eventDate, String startTime, String endDate, String endTime,
            String location, String address, Long homeTeamId, Long awayTeamId,
            String delegateOid, String delegateDisplayName, String status) {
        Data cancelled(int revision) {
            return new Data(matchId,competitionId,competitionName,sportId,revision,title,description,coverImage,
                    eventDate,startTime,endDate,endTime,location,address,homeTeamId,awayTeamId,delegateOid,
                    delegateDisplayName,"CANCELLED");
        }
    }
    public record Envelope(String messageId, String occurredAt, String type, Data data) {}

    public void publish(Match match, Competition competition, Enrollment home, Enrollment away) {
        var data = new Data(match.getId(),competition.getId(),competition.getName(),competition.getSportId(),
                match.getScheduleVersion(),title(home.getTeamName() + " vs " + away.getTeamName()),
                competition.getName() + " — " + match.getRound().getLabel(),competition.getCompetitionImg(),
                match.getDate().toString(),match.getTime().toString(),match.getEndDate().toString(),match.getEndTime().toString(),
                match.getLocation(),match.getAddress(),match.getHomeTeamId(),match.getAwayTeamId(),
                match.getDelegateOid(),match.getDelegateDisplayName(),"PUBLISHED");
        outbox.enqueue(ROUTING_KEY,match.getId(),match.getScheduleVersion(),
                new Envelope(UUID.randomUUID().toString(),Instant.now().toString(),ROUTING_KEY,data));
    }
    public void cancel(Match match) {
        if (!match.isScheduleConfirmed()) return;
        var previous = messages.findByRoutingKeyAndAggregateIdAndRevision(ROUTING_KEY,match.getId(),match.getScheduleVersion())
                .orElseThrow(() -> new IllegalStateException("No existe el snapshot de agenda confirmado"));
        try {
            var old = rabbitObjectMapper.readValue(previous.getPayload(),Envelope.class);
            match.setScheduleVersion(match.getScheduleVersion() + 1);
            match.setScheduleConfirmed(false);
            outbox.enqueue(ROUTING_KEY,match.getId(),match.getScheduleVersion(),
                    new Envelope(UUID.randomUUID().toString(),Instant.now().toString(),ROUTING_KEY,
                            old.data().cancelled(match.getScheduleVersion())));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo recuperar el snapshot de agenda",ex);
        }
    }

    private static String title(String value) { return value.length() <= 255 ? value : value.substring(0,255); }
}
