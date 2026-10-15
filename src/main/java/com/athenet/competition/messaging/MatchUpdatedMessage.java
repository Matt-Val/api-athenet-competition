package com.athenet.competition.messaging;

/*
    Data de partido.actualizado: lo que MS-Eventos necesita para mantener la copia del
    partido en su calendario. Fecha y hora van como texto (yyyy-MM-dd / HH:mm).
*/
public record MatchUpdatedMessage(
    Long matchId,
    Long competitionId,
    Long sportId,
    String title,
    String description,
    String competitionImg,
    String date,
    String time,
    String location,
    String address,
    Long homeTeamId,
    Long awayTeamId,
    String delegateOid,
    String delegateDisplayName
) {
}
