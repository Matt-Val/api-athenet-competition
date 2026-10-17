package com.athenet.competition.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.athenet.competition.dto.response.MatchResponse;
import com.athenet.competition.dto.response.TeamMatchResponse;
import com.athenet.competition.mapper.MatchMapper;
import com.athenet.competition.model.enums.MatchStatus;
import com.athenet.competition.service.MatchService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/*
    GET /api/public/matches/{id} — marcador de un partido. La copia en Eventos guarda
    el id del partido (competitionMatchId), así que un BFF puede mostrar el resultado
    en el detalle del evento sin pasar por competitionId.
*/
@RestController
@RequestMapping("/api/public/matches")
@RequiredArgsConstructor
public class PublicMatchController {

    private final MatchService matchService;
    private final MatchMapper matchMapper;

    @GetMapping("/{id}")
    public ResponseEntity<MatchResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(matchMapper.toResponse(matchService.getById(id)));
    }

    /*
        GET /api/public/matches?teamId&status&limit — últimos partidos del equipo, con los nombres
        y logos de ambos equipos. teamId es obligatorio. Nunca incluye borradores (DRAFT).
    */
    @GetMapping
    public ResponseEntity<List<TeamMatchResponse>> listByTeam(
            @RequestParam Long teamId,
            @RequestParam(required = false) MatchStatus status,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int limit) {
        List<TeamMatchResponse> response = matchService.listForTeam(teamId, status, limit).stream()
                .map(team -> matchMapper.toTeamResponse(team.match(), team.competition(), team.home(), team.away()))
                .toList();
        return ResponseEntity.ok(response);
    }
}
