package com.athenet.competition.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.athenet.competition.dto.request.*;
import com.athenet.competition.dto.response.*;
import com.athenet.competition.mapper.*;
import com.athenet.competition.service.*;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/*
    Rol Director. La seguridad real (JWT + ROLE_DIRECTOR) ya está conectada
    en SecurityConfig.

    La edición de partidos comprueba su pertenencia a la competencia. Los resultados
    no pasan por acá: los carga el delegado (DelegateMatchController).
*/
@RestController
@RequestMapping("/api/admin/competitions")
@RequiredArgsConstructor
public class AdminCompetitionController {

    private final CompetitionService competitionService;
    private final EnrollmentService enrollmentService;
    private final MatchService matchService;

    private final CompetitionMapper competitionMapper;
    private final EnrollmentMapper enrollmentMapper;
    private final MatchMapper matchMapper;

    /* 
        Listado de competencias, con filtro opcional por deporte. 
        El filtro es útil para el admin Angular, que tiene un selector de deportes.
    */
    @GetMapping
    public ResponseEntity<List<CompetitionResponse>> list(@RequestParam(required = false) Long sportId) {
        List<CompetitionResponse> response = competitionService.listForAdmin(sportId).stream()
                .map(competitionMapper::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    // Detalle en cualquier estado, incluido DRAFT
    @GetMapping("/{id}")
    public ResponseEntity<CompetitionResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(competitionMapper.toResponse(competitionService.getById(id)));
    }

    /*
        Crear, actualizar, cambiar estado y eliminar competencias.
        El create/update devuelve 201/200 con el recurso creado/actualizado.
    */
    @PostMapping
    public ResponseEntity<CompetitionResponse> create(@Valid @RequestBody CompetitionRequest request) {
        CompetitionResponse created = competitionMapper.toResponse(
                competitionService.create(competitionMapper.toEntity(request)));
        return ResponseEntity.created(URI.create("/api/admin/competitions/" + created.id())).body(created);
    }

    /* 
        Actualizar competencia: el request body es el mismo que para crear, 
        pero el id de la competencia se pasa por path variable.
    */
    @PutMapping("/{id}")
    public ResponseEntity<CompetitionResponse> update(@PathVariable Long id,
            @Valid @RequestBody CompetitionRequest request) {
        CompetitionResponse updated = competitionMapper.toResponse(
                competitionService.update(id, competitionMapper.toEntity(request)));
        return ResponseEntity.ok(updated);
    }
    
    /*
        Cambiar estado de la competencia: el request body es solo el nuevo estado.
    */

    @PatchMapping("/{id}/status")
    public ResponseEntity<CompetitionResponse> changeStatus(@PathVariable Long id,
            @Valid @RequestBody CompetitionStatusRequest request) {
        CompetitionResponse updated = competitionMapper.toResponse(
                competitionService.changeStatus(id, request.status()));
        return ResponseEntity.ok(updated);
    }

    /*
        Eliminar competencia: devuelve 204 No Content. 
        La eliminación puede implicar acciones adicionales (ej: borrar partidos).
    */

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        competitionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // Nómina de la competencia: el director sortea los octavos desde aquí
    @GetMapping("/{id}/teams")
    public ResponseEntity<List<EnrollmentResponse>> listTeams(@PathVariable Long id) {
        List<EnrollmentResponse> response = enrollmentService.listByCompetition(id).stream()
                .map(enrollmentMapper::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    /*
        Inscribir y desinscribir equipos en la competencia.
        El enroll devuelve 201 con el recurso creado, el unenroll devuelve 204.
    */
    @PostMapping("/{id}/teams")
    public ResponseEntity<EnrollmentResponse> enroll(@PathVariable Long id,
            @Valid @RequestBody EnrollmentRequest request) {
        EnrollmentResponse created = enrollmentMapper.toResponse(
                enrollmentService.enroll(id, request.teamId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /* 
        Desinscribir equipo de la competencia: devuelve 204 No Content. 
        La desinscripción puede implicar acciones adicionales (ej: borrar partidos).
    */
    @DeleteMapping("/{id}/teams/{teamId}")
    public ResponseEntity<Void> unenroll(@PathVariable Long id, @PathVariable Long teamId) {
        enrollmentService.unenroll(id, teamId);
        return ResponseEntity.noContent().build();
    }

    /*
        Llave de la copa, ordenada por ronda y posición. Incluye borradores.
    */
    @GetMapping("/{id}/matches")
    public ResponseEntity<List<MatchResponse>> listMatches(@PathVariable Long id) {
        List<MatchResponse> response = matchService.listByCompetition(id).stream()
                .map(matchMapper::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    /*
        Programar un partido (fecha, hora, lugar, delegado) y sortear los equipos de octavos.
        El resultado no se toca acá: solo lo carga el delegado asignado.
    */
    @PutMapping("/{id}/matches/{matchId}")
    public ResponseEntity<MatchResponse> updateMatch(@PathVariable Long id, @PathVariable Long matchId,
            @Valid @RequestBody MatchUpdateRequest request) {
        MatchResponse updated = matchMapper.toResponse(
                matchService.updateMatch(id, matchId, matchMapper.toEntity(request)));
        return ResponseEntity.ok(updated);
    }
}
