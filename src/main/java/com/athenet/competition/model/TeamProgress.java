package com.athenet.competition.model;

import com.athenet.competition.model.enums.MatchRound;
import com.athenet.competition.model.enums.TeamOutcome;

// Cómo le fue a un equipo en una competencia. Se calcula con sus partidos, no se guarda.
public record TeamProgress(
    MatchRound round,
    TeamOutcome outcome,
    int played,
    int won,
    int lost
) {
}
