package com.athenet.competition.messaging;

/*
    Contrato del topic "marcador" consumido por ms-statistics y la web pública.
    El marcador viaja absoluto para que los eventos sean idempotentes.
*/
public record ScoreEvent(
        Long idEncuentro,
        String equipoLocal,
        String equipoVisita,
        int puntosLocal,
        int puntosVisita,
        String tipo
) {
    public static final String TIPO_INICIADO = "INICIADO";
    public static final String TIPO_PUNTO = "PUNTO";
    public static final String TIPO_FINALIZADO = "FINALIZADO";
}
