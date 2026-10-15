package com.athenet.competition.messaging;

public record ResultRegisteredMessage(
    Long matchId,
    Long competitionId,
    Long sportId,
    int version,
    Long homeTeamId,
    Long awayTeamId,
    Long homeInstitutionId,
    Long awayInstitutionId,
    Integer homeScore,
    Integer awayScore,
    Points points
) {
    public record Points(int win, int draw, int loss) {
    }
}
