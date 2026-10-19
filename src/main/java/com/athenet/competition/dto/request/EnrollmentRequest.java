package com.athenet.competition.dto.request;

import jakarta.validation.constraints.NotNull;

// POST /api/admin/competitions/{id}/teams
public record EnrollmentRequest(
    @NotNull(message = "El id del equipo es obligatorio")
    Long teamId
) {
}
