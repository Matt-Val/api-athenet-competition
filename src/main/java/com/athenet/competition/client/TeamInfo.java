package com.athenet.competition.client;

// Lo que EnrollmentService necesita de un equipo de MS-Instituciones,
// ya combinado (equipo + su sede) y en inglés.

public record TeamInfo(
    Long id,
    String name,
    String logoUrl,
    Long sportId,
    Long institutionId,
    String institutionName
) {
}
