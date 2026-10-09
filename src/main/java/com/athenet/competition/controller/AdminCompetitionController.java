package com.athenet.competition.controller;

import java.net.URI;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.athenet.competition.dto.request.*;
import com.athenet.competition.dto.response.*;
import com.athenet.competition.mapper.*;
import com.athenet.competition.service.*;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/* 
    Rol Director. La seguridad real (JWT + ROLE_DIRECTOR) se conecta 
    cuando armemos el SecurityConfig como en MS-events.

    Nota: Faltan dos endpoints del documento en el controller:
        - POST /{id}/teams (inscribir grupo): Necesita el cliente REST hacia 
            MS-Instituciones para resolver institutionId/teamName/teamLogoUrl a partir de teamId,
            y validar que esté activo y sea del mismo deporte.
        - POST /{id}/matches/{machId}/simulate: Necesita saber el deporte real
            de la competencia (fútbol vs. básquet) para eligir el rango de marcador,
            y esa info también vive en MS-Instituciones.

    LO AGREGAMOS CUANDO SE CONSTRUYA ESE CLIENTE.
*/
@RestController
@RequestMapping("/api/admin/competitions")
@RequiredArgsConstructor
public class AdminCompetitionController {

    private final CompetitionService competitionService;
    private final EnrollmentService enrollmentService;
    private final MatchService matchService;

    private final CompetitionMapper competitionMapper;
    private final MatchMapper matchMapper;

    @GetMapping
    public ResponseEntity<List<CompetitionResponse>> list(@RequestParam(required = false) Long sportId) {
        List<CompetitionResponse> response = competitionService.listForAdmin(sportId).stream()
                .map(competitionMapper::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<CompetitionResponse> create(@Valid @RequestBody CompetitionRequest request) {
        CompetitionResponse created = competitionMapper.toResponse(
                competitionService.create(competitionMapper.toEntity(request)));
        return ResponseEntity.created(URI.create("/api/admin/competitions/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CompetitionResponse> update(@PathVariable Long id,
            @Valid @RequestBody CompetitionRequest request) {
        CompetitionResponse updated = competitionMapper.toResponse(
                competitionService.update(id, competitionMapper.toEntity(request)));
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<CompetitionResponse> changeStatus(@PathVariable Long id,
            @Valid @RequestBody CompetitionStatusRequest request) {
        CompetitionResponse updated = competitionMapper.toResponse(
                competitionService.changeStatus(id, request.status()));
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        competitionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/teams/{teamId}")
    public ResponseEntity<Void> unenroll(@PathVariable Long id, @PathVariable Long teamId) {
        enrollmentService.unenroll(id, teamId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/matches/{matchId}/result")
    public ResponseEntity<MatchResponse> registerResult(@PathVariable Long id, @PathVariable Long matchId,
            @Valid @RequestBody MatchResultRequest request) {
        MatchResponse updated = matchMapper.toResponse(
                matchService.registerResult(matchId, request.homeScore(), request.awayScore()));
        return ResponseEntity.ok(updated);
    }
}
