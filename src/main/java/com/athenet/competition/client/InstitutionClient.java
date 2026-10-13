package com.athenet.competition.client;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.athenet.competition.exception.ConflictException;
import com.athenet.competition.exception.ResourceNotFoundException;
import com.athenet.competition.exception.UpstreamServiceException;

import lombok.RequiredArgsConstructor;

/*
    Llamadas GET públicas a MS-Instituciones, sin JWT. Las respuestas
    crudas (TeamApiResponse/CampusApiResponse/SportApiResponse) copian el 
    contrato en español de ese servicio tal cual lo expone - son solo para 
    deserializar el JSON, no se traducen, lo que el resto de competencia
    consume es TeamInfo/SportInfo, ya en inglés y con solo lo que necesitamos.
*/
@Component
@RequiredArgsConstructor
public class InstitutionClient {

    private final RestClient institutionRestClient;

    /* 
        Equipo + su sede combinados, el equipo solo trae sedeId, el 
        institutionId sale de la sede (EquipoResponse todavía no lo expone
        directo, cambio pendiente en ms-instituciones).
    */
    public TeamInfo getActiveTeam(Long teamId) {
        TeamApiResponse team = get("/api/v1/equipos/" + teamId, TeamApiResponse.class)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "El equipo " + teamId + " no se encuentra registrado."));

        if (!team.activo()) {
            throw new ConflictException("El equipo " + teamId + " no está activo");
        }

        CampusApiResponse campus = get("/api/v1/sedes/" + team.sedeId(), CampusApiResponse.class)
                .orElseThrow(() -> new UpstreamServiceException(
                        "No se pudo resolver la sede " + team.sedeId() + " del equipo " + teamId));

        return new TeamInfo(team.id(), team.nombre(), team.logoUrl(), team.deporteId(),
                campus.institucionId(), campus.institucionNombre());
    }

    public SportInfo getSport(Long sportId) {
        SportApiResponse sport = get("/api/v1/deportes/" + sportId, SportApiResponse.class)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "El deporte " + sportId + " no se encuentra registrado."));

        return new SportInfo(sport.id(), sport.nombre());
    }

    private <T> Optional<T> get(String path, Class<T> type) {
        try {
            return Optional.ofNullable(institutionRestClient.get().uri(path).retrieve().body(type));
        } catch (HttpClientErrorException.NotFound ex) {
            return Optional.empty();
        } catch (RestClientException ex) {
            throw new UpstreamServiceException("MS-Instituciones no respondió (" + path + ")", ex);
        }
    }

    private record TeamApiResponse(Long id, String nombre, String categoria, String entrenador,
            String logoUrl, Long sedeId, String sedeNombre, Long deporteId, String deporteNombre, boolean activo) {
    }

    private record CampusApiResponse(Long id, String nombre, String ciudad, String direccion,
            Long institucionId, String institucionNombre) {
    }

    private record SportApiResponse(Long id, String nombre, String descripcion) {
    }
}
