package com.athenet.competition.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;

import com.athenet.competition.dto.response.*;
import com.athenet.competition.mapper.*;
import com.athenet.competition.service.*;
import com.athenet.competition.model.enums.CompetitionStatus;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

// Lecturas públicas, sin token. Las consume bff-public y para validaciones, MS-Eventos.
@RestController
@RequestMapping("/api/public/competitions")
@RequiredArgsConstructor
public class PublicCompetitionController {

    private final CompetitionService competitionService;
    private final EnrollmentService enrollmentService;
    private final MatchService matchService;

    private final CompetitionMapper competitionMapper;
    private final EnrollmentMapper enrollmentMapper;
    private final MatchMapper matchMapper;

    /*
        Nunca incluye borradores (DRAFT).
        Solo incluye competencias activas y finalizadas.

    */
    @GetMapping
    public ResponseEntity<List<CompetitionResponse>> list(
            @RequestParam(required = false) Long sportId,
            @RequestParam(required = false) CompetitionStatus status) {
        List<CompetitionResponse> response = competitionService.listForPublic(sportId, status).stream()
                .map(competitionMapper::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    /*
        Con teamId: las competencias del equipo, las más recientes primero, y cómo le fue
        en cada una (teamProgress). Nunca incluye borradores (DRAFT).
    */
    @GetMapping(params = "teamId")
    public ResponseEntity<List<TeamCompetitionResponse>> listByTeam(
            @RequestParam Long teamId,
            @RequestParam(required = false) Long sportId,
            @RequestParam(required = false) CompetitionStatus status,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int limit) {
        List<TeamCompetitionResponse> response = competitionService.listForTeam(teamId, sportId, status, limit).stream()
                .map(team -> competitionMapper.toTeamResponse(team.competition(), team.progress()))
                .toList();
        return ResponseEntity.ok(response);
    }

    /*
        Devuelve la competencia, sus equipos y sus partidos.
        Nunca incluye borradores (DRAFT).
        Solo incluye competencias activas y finalizadas.
    */
    @GetMapping("/{id}")
    public ResponseEntity<CompetitionResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(competitionMapper.toResponse(competitionService.getById(id)));
    }

    /* 
        Devuelve los equipos inscritos en la competencia. 
        Nunca incluye borradores (DRAFT).
        Solo incluye competencias activas y finalizadas.
    */
    @GetMapping("/{id}/teams")
    public ResponseEntity<List<EnrollmentResponse>> listTeams(@PathVariable Long id) {
        List<EnrollmentResponse> response = enrollmentService.listByCompetition(id).stream()
                .map(enrollmentMapper::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    /* 
        Devuelve los partidos de la competencia. 
        Nunca incluye borradores (DRAFT).
        Solo incluye competencias activas y finalizadas.
    */
    @GetMapping("/{id}/matches")
    public ResponseEntity<List<MatchResponse>> listMatches(@PathVariable Long id) {
        List<MatchResponse> response = matchService.listByCompetition(id).stream()
                .map(matchMapper::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }
}
