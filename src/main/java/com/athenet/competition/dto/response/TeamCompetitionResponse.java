package com.athenet.competition.dto.response;

import com.athenet.competition.model.TeamProgress;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

// Los mismos campos de CompetitionResponse, más cómo le fue al equipo en esa competencia
public record TeamCompetitionResponse(
    @JsonUnwrapped CompetitionResponse competition,
    TeamProgress teamProgress
) {
}
