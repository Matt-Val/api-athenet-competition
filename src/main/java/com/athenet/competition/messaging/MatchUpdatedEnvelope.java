package com.athenet.competition.messaging;

// Sobre partido.actualizado. Record concreto, igual que ResultRegisteredEnvelope.
public record MatchUpdatedEnvelope(
    String messageId,
    String occurredAt,
    String type,
    MatchUpdatedMessage data
) {
}
