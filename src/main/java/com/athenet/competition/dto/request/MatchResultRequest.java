package com.athenet.competition.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

// PUT /api/admin/competitions/{id}/matches/{matchId}/result
public record MatchResultRequest(
    @NotNull(message = "El marcador local es obligatorio")
    @PositiveOrZero(message = "El marcador local no puede ser negativo")
    Integer homeScore,

    @NotNull(message = "El marcador visita es obligatorio")
    @PositiveOrZero(message = "El marcador visita no puede ser negativo")
    Integer awayScore
) {
}
