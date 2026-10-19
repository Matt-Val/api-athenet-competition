package com.athenet.competition.messaging;

/*
    Nombres y ronda van listos para mostrar. version es 1 al finalizar el partido y sube en
    cada corrección: el consumidor procesa solo si es mayor que la última guardada por matchId.
*/
public record ResultRegisteredMessage(
    Long matchId,
    Long competitionId,
    String competitionName,
    Long sportId,
    String round,
    int version,
    Long homeTeamId,
    Long awayTeamId,
    String homeTeamName,
    String awayTeamName,
    Long homeInstitutionId,
    Long awayInstitutionId,
    Integer homeScore,
    Integer awayScore,
    Points points,
    String matchDate
) {
    public record Points(int win, int draw, int loss) {
    }
}
