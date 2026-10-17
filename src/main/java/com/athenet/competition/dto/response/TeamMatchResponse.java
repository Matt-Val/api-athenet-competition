package com.athenet.competition.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;

import com.athenet.competition.model.enums.MatchRound;
import com.athenet.competition.model.enums.MatchStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

// Partido con los nombres y logos de ambos equipos, sin datos del delegado
public record TeamMatchResponse(
    Long id,
    CompetitionSummary competition,
    MatchRound round,
    LocalDate date,
    @JsonFormat(pattern = "HH:mm") LocalTime time,
    String location,
    String address,
    MatchStatus status,
    TeamScore home,
    TeamScore away
) {

    public record CompetitionSummary(Long id, String name, String season, String competitionImg) {
    }

    // score es null hasta que el partido termina
    public record TeamScore(Long teamId, String name, String logoUrl, Integer score) {
    }
}
