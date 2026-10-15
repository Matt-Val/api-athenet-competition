package com.athenet.competition.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.athenet.competition.dto.response.MatchResponse;
import com.athenet.competition.mapper.MatchMapper;
import com.athenet.competition.service.MatchService;

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
}
