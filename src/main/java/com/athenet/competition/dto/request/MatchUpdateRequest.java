package com.athenet.competition.dto.request;

import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/*
    PUT /api/admin/competitions/{id}/matches/{matchId}. Se envía el partido completo:
    lo que todavía no se define va en null. Los equipos solo se eligen en octavos.
*/
public record MatchUpdateRequest(

    LocalDate date,

    @JsonFormat(pattern = "HH:mm")
    LocalTime time,

    @Size(max = 255, message = "El lugar no puede superar los 255 caracteres")
    String location,

    @Size(max = 255, message = "La dirección no puede superar los 255 caracteres")
    String address,

    Long homeTeamId,

    Long awayTeamId,

    @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
            message = "El oid del delegado no es válido")
    String delegateOid,

    @Size(max = 256, message = "El nombre del delegado no puede superar los 256 caracteres")
    String delegateDisplayName,
    LocalDate endDate,
    @JsonFormat(pattern = "HH:mm") LocalTime endTime
) {
}
