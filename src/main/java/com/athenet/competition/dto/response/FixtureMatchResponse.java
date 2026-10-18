package com.athenet.competition.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;

import com.athenet.competition.model.enums.MatchRound;
import com.athenet.competition.model.enums.MatchStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

// Partido de la llave para el sitio publico: competencia y equipos resueltos, sin delegado ni version.
public record FixtureMatchResponse(
    Long id,
    CompetitionSummary competition,
    MatchRound round,
    int position,
    LocalDate date,
    @JsonFormat(pattern = "HH:mm") LocalTime time,
    String location,
    String address,
    MatchStatus status,
    FixtureSide home,
    FixtureSide away
) {

    public record CompetitionSummary(Long id, String name, String season, Long sportId, String competitionImg) {
    }

    // El lado es null mientras la ronda anterior no lo define; score, hasta que el delegado lo carga
    public record FixtureSide(Long teamId, Long institutionId, String name, String logoUrl, Integer score) {
    }
}
