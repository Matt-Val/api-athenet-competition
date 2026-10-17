package com.athenet.competition.messaging;

/*
    Sobre resultado.registrado. Concreto ya que Notificaciones debería copiar este mismo patrón
    (record contreto, no MessageEnvelope genérico) cuando armen su propio listener para este mensaje.
*/
public record ResultRegisteredEnvelope(
    String messageId,
    String occurredAt,
    String type,
    ResultRegisteredMessage data
) {
}
