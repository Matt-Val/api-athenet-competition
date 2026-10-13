package com.athenet.competition.dto.request;

import java.time.LocalDate;

import com.athenet.competition.model.enums.CompetitionType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/* 
    Se usa para crear o actualizar (POST/PUT /api/admin/competitions).
    WinPoints/DrawPoints/LossPoints son opcionales: si vienen null,
    el mapper aplica los defaults 3/1/0.

*/

public record CompetitionRequest(

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
    String name,

    @NotNull(message = "El tipo es obligatorio")
    CompetitionType type,

    @NotNull(message = "El id del deporte es obligatorio")
    Long sportId,

    @NotBlank(message = "La temporada es obligatoria")
    @Size(max = 20, message = "La temporada no puede superar los 20 caracteres")
    String season,

    String description,

    LocalDate startDate,

    LocalDate endDate,

    @PositiveOrZero(message = "Los puntos por victoria no pueden ser negativos")
    Integer winPoints,

    @PositiveOrZero(message = "Los puntos por empate no pueden ser negativos")
    Integer drawPoints,

    @PositiveOrZero(message = "Los puntos por derrota no pueden ser negativos")
    Integer lossPoints
) {
}
