package com.athenet.competition.dto.response;

public record EnrollmentResponse(
    Long id,
    Long competitionId,
    Long teamId,
    Long institutionId,
    String teamName,
    String teamLogoUrl
) {
}
