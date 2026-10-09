package com.athenet.competition.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.athenet.competition.dto.response.MatchResponse;
import com.athenet.competition.mapper.MatchMapper;
import com.athenet.competition.service.MatchService;

import lombok.RequiredArgsConstructor;

/* 
    GET /api/public/matches?eventId= — lo usa el BFF para mostrar el marcador
    en el detalle de un evento (§5.2), sin tener que pasar por competitionId.
*/
@RestController
@RequestMapping("/api/public/matches")
@RequiredArgsConstructor
public class PublicMatchController {

    private final MatchService matchService;
    private final MatchMapper matchMapper;

    @GetMapping
    public ResponseEntity<MatchResponse> getByEventId(@RequestParam Long eventId) {
        return ResponseEntity.ok(matchMapper.toResponse(matchService.getByEventIdOrThrow(eventId)));
    }
}
