package com.athenet.competition.messaging;

/* 
    Sobre de evento.creado/actualizado/cancelado.
    Record con propósito: con un MessageEnvelope genérico, Jackson no puede
    saber en runtime que T=EventMessage y la deserialización del @RabbitListener falla con
    "Cannot convert from LinkedHashMap to MessageEnvelope". Cada mensaje necesita
    su propio record concreto.
*/
public record EventEnvelope(
    String messageId,
    String occurredAt,
    String type,
    EventMessage data
) {
}
