package com.athenet.competition.dto.response;

import java.time.LocalDate;

import com.athenet.competition.model.enums.CompetitionStatus;
import com.athenet.competition.model.enums.CompetitionType;

public record CompetitionResponse(
    Long id,
    String name,
    CompetitionType type,
    Long sportId,
    String season,
    String description,
    String competitionImg,
    CompetitionStatus status,
    LocalDate startDate,
    LocalDate endDate,
    int winPoints,
    int drawPoints,
    int lossPoints
) {
}
