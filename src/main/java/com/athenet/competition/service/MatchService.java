package com.athenet.competition.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.athenet.competition.exception.ConflictException;
import com.athenet.competition.exception.ResourceNotFoundException;
import com.athenet.competition.model.Match;
import com.athenet.competition.model.enums.MatchStatus;
import com.athenet.competition.repository.MatchRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchService {

    private final MatchRepository matchRepository;

    public Match getById(Long id) {
        return findEntityById(id);
    }

    public Optional<Match> getByEventId(Long eventId) {
        return matchRepository.findByEventId(eventId);
    }


    /* 
        GET /api/public/matches?eventId= — acá "no existe" sí es un error (404),
        a diferencia de getByEventId (Optional), que usa el futuro listener de
        RabbitMQ donde no encontrar el partido es un caso normal.
    */
    public Match getByEventIdOrThrow(Long eventId) {
        return matchRepository.findByEventId(eventId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No hay un partido asociado al evento " + eventId));
    }

    public List<Match> listByCompetition(Long competitionId) {
        return matchRepository.findByCompetitionId(competitionId);
    }


    /* 
        Crea o actualiza el partido a partir de evento.creado / evento.actualizado.
        El upsert es por eventId: si ya existe un Match para ese evento se actualizan sus datos
        de agenda. Si no, se crea uno nuevo en estado SCHEDULED. El resultado (homeScore/awayScore)
        nunca se toca acá - eso solo lo cambia registerResult.
    */
    @Transactional
    public Match upsertFromEvent(Long eventId, String eventInternalId, Long competitionId,
            LocalDate date, LocalTime time, Long homeTeamId, Long awayTeamId) {
        Match match = matchRepository.findByEventId(eventId).orElseGet(Match::new);

        match.setEventId(eventId);
        match.setEventInternalId(eventInternalId);
        match.setCompetitionId(competitionId);
        match.setDate(date);
        match.setTime(time);
        match.setHomeTeamId(homeTeamId);
        match.setAwayTeamId(awayTeamId);
        if (match.getStatus() == null) {
            match.setStatus(MatchStatus.SCHEDULED);
        }

        return matchRepository.save(match);
    }

    /*
        evento.cancelado: deja el partido en CANCELLED, sin borrarlo
        (a diferencia de cuando se quita competitionId, aá si hay un historial de que el evento
        existió).
    */
    @Transactional
    public void cancelByEventId(Long eventId) {
        matchRepository.findByEventId(eventId)
                .ifPresent(match -> {
                    match.setStatus(MatchStatus.CANCELLED);
                    matchRepository.save(match);
                });
    }


    /* 
        Si evento.actualizado quita el competitionId y el partido todavía no tiene resultado,
        se elimina de la competencia. SI ya tiene resultado se conserva: borrar un resultado ya jugado
        rompería el historial de la tabla de posiciones.
    */
    @Transactional
    public void removeIfUnresolved(Long eventId) {
        matchRepository.findByEventId(eventId)
                .filter(match -> match.getStatus() == MatchStatus.SCHEDULED)
                .ifPresent(matchRepository::delete);
    }


    /* 
        PUT ../partidos/{id}/resultado. Si el partido estaba en SCHEDULED pasa a 
        FINISHED. Si ya estaba en FINISHED es una corrección y versión sube igual.
        Nota: Todavía no publica resultado.registrado - se conecta cuando armemos la capa de mensajería.
    */
    @Transactional
    public Match registerResult(Long matchId, int homeScore, int awayScore) {
        Match match = findEntityById(matchId);

        if (match.getStatus() == MatchStatus.CANCELLED) {
            throw new ConflictException("No se puede registrar un resultado para un partido cancelado");
        }

        match.setHomeScore(homeScore);
        match.setAwayScore(awayScore);
        match.setStatus(MatchStatus.FINISHED);
        match.setSimulated(false);
        match.setVersion(match.getVersion() + 1);

        return matchRepository.save(match);
    }

    private Match findEntityById(Long id) {
        return matchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Partido con id " + id + " no fue encontrado"));
    }
}
