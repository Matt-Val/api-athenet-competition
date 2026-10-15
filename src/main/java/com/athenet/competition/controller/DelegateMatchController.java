package com.athenet.competition.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import com.athenet.competition.dto.request.MatchResultRequest;
import com.athenet.competition.dto.response.MatchResponse;
import com.athenet.competition.mapper.MatchMapper;
import com.athenet.competition.service.MatchService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/*
    Rol Delegado (ROLE_DELEGATE en SecurityConfig). Solo ve y gestiona los partidos que
    tiene asignados: MatchService compara el claim oid del token con el delegado del partido.
*/
@RestController
@RequestMapping("/api/delegate/matches")
@RequiredArgsConstructor
public class DelegateMatchController {

    private final MatchService matchService;
    private final MatchMapper matchMapper;

    // "Mis eventos": partidos asignados en competencias ya iniciadas
    @GetMapping
    public ResponseEntity<List<MatchResponse>> list(@AuthenticationPrincipal Jwt jwt) {
        List<MatchResponse> response = matchService.listForDelegate(oid(jwt)).stream()
                .map(matchMapper::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{matchId}/start")
    public ResponseEntity<MatchResponse> start(@PathVariable Long matchId, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(matchMapper.toResponse(matchService.start(matchId, oid(jwt))));
    }

    /*
        Finaliza el partido con su resultado, o lo corrige si ya estaba finalizado.
        Devuelve 200 con el partido actualizado.
    */
    @PutMapping("/{matchId}/result")
    public ResponseEntity<MatchResponse> registerResult(@PathVariable Long matchId, @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody MatchResultRequest request) {
        MatchResponse updated = matchMapper.toResponse(
                matchService.registerResult(matchId, oid(jwt), request.homeScore(), request.awayScore()));
        return ResponseEntity.ok(updated);
    }

    private static String oid(Jwt jwt) {
        return jwt.getClaimAsString("oid");
    }
}
