package com.athenet.competition.dto.request;

import com.athenet.competition.model.enums.CompetitionStatus;

import jakarta.validation.constraints.NotNull;

// PATCH /api/admin/competitions/{id}/status
public record CompetitionStatusRequest(
    @NotNull(message = "El estado es obligatorio")
    CompetitionStatus status
) {
}
