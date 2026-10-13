package com.athenet.competition.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;

import com.athenet.competition.model.enums.MatchStatus;

public record MatchResponse(
    Long id,
    Long competitionId,
    Long eventId,
    String eventInternalId,
    LocalDate date,
    LocalTime time,
    Long homeTeamId,
    Long awayTeamId,
    MatchStatus status,
    Integer homeScore,
    Integer awayScore,
    boolean simulated,
    int version
) {
}
