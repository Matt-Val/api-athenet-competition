package com.athenet.competition.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;

import com.athenet.competition.model.enums.MatchRound;
import com.athenet.competition.model.enums.MatchStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

public record MatchResponse(
    Long id,
    Long competitionId,
    MatchRound round,
    int position,
    LocalDate date,
    @JsonFormat(pattern = "HH:mm") LocalTime time,
    String location,
    String address,
    Long homeTeamId,
    Long awayTeamId,
    String delegateOid,
    String delegateDisplayName,
    MatchStatus status,
    Integer homeScore,
    Integer awayScore,
    int version,
    LocalDate endDate,
    @JsonFormat(pattern = "HH:mm") LocalTime endTime,
    boolean scheduleConfirmed,
    int scheduleVersion
) {
}
