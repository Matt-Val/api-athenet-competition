package com.athenet.competition.messaging;

/* 
    Data de evento.creado/actualizado/cancelado
*/
public record EventMessage(
    Long eventId,
    String internalId,
    String type,
    String status,
    Long competitionId,
    String eventDate,
    String startTime,
    Long teamOneId,
    Long teamTwoId
) {
}
