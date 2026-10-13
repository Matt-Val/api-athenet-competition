package com.athenet.competition.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.athenet.competition.exception.ConflictException;
import com.athenet.competition.exception.ResourceNotFoundException;
import com.athenet.competition.client.InstitutionClient;
import com.athenet.competition.messaging.ResultEventPublisher;
import com.athenet.competition.model.Competition;
import com.athenet.competition.model.Enrollment;
import com.athenet.competition.model.Match;
import com.athenet.competition.model.enums.MatchStatus;
import com.athenet.competition.repository.CompetitionRepository;
import com.athenet.competition.repository.EnrollmentRepository;
import com.athenet.competition.repository.MatchRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchService {

    private final MatchRepository matchRepository;
    private final CompetitionRepository competitionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ResultEventPublisher resultEventPublisher;
    private final InstitutionClient institutionClient;

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
        PUT .../partidos/{id}/resultado. Si el partido estaba en SCHEDULED pasa a
        FINISHED. Si ya estaba en FINISHED es una corrección y version sube igual.
        Siempre publica resultado.registrado (§7) — Ranking y Eventos lo consumen
        para recalcular la tabla y marcar el evento FINISHED respectivamente.
    */
    @Transactional
    public Match registerResult(Long competitionId, Long matchId, int homeScore, int awayScore) {
        Match match = findCompetitionMatch(competitionId, matchId);

        if (match.getStatus() == MatchStatus.CANCELLED) {
            throw new ConflictException("No se puede registrar un resultado para un partido cancelado");
        }

        if (homeScore < 0 || awayScore < 0) {
            throw new ConflictException("El marcador no puede ser negativo");
        }
        return saveResult(match, homeScore, awayScore, false);
    }

    @Transactional
    public Match simulate(Long competitionId, Long matchId) {
        Match match = findCompetitionMatch(competitionId, matchId);
        if (match.getStatus() != MatchStatus.SCHEDULED) {
            throw new ConflictException("Solo se pueden simular partidos programados");
        }
        Competition competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new ResourceNotFoundException("Competencia no encontrada"));
        String sport = Normalizer.normalize(institutionClient.getSport(competition.getSportId()).name(),
                Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
        int min;
        int max;
        boolean basketball;
        switch (sport) {
            case "futbol", "football", "soccer" -> { min = 0; max = 5; basketball = false; }
            case "basquetbol", "basquet", "baloncesto", "basketball" -> { min = 50; max = 110; basketball = true; }
            default -> throw new ConflictException("Simulación no disponible para el deporte " + sport);
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int homeScore = random.nextInt(min, max + 1);
        int awayScore;
        do {
            awayScore = random.nextInt(min, max + 1);
        } while (basketball && homeScore == awayScore);
        return saveResult(match, homeScore, awayScore, true);
    }

    private Match findCompetitionMatch(Long competitionId, Long matchId) {
        Match match = findEntityById(matchId);
        if (!competitionId.equals(match.getCompetitionId())) {
            throw new ResourceNotFoundException("El partido " + matchId + " no pertenece a la competencia " + competitionId);
        }
        return match;
    }

    private Match saveResult(Match match, int homeScore, int awayScore, boolean simulated) {
        match.setHomeScore(homeScore);
        match.setAwayScore(awayScore);
        match.setStatus(MatchStatus.FINISHED);
        match.setSimulated(simulated);
        match.setVersion(match.getVersion() + 1);

        Match saved = matchRepository.save(match);
        publishResult(saved);
        return saved;
    }

    private void publishResult(Match match) {
        Competition competition = competitionRepository.findById(match.getCompetitionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Competencia con id " + match.getCompetitionId() + " no fue encontrada"));
        Enrollment home = findEnrollment(match.getCompetitionId(), match.getHomeTeamId());
        Enrollment away = findEnrollment(match.getCompetitionId(), match.getAwayTeamId());

        resultEventPublisher.publish(match, competition, home, away);
    }

    private Enrollment findEnrollment(Long competitionId, Long teamId) {
        return enrollmentRepository.findByCompetitionIdAndTeamId(competitionId, teamId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "El equipo " + teamId + " no está inscrito en la competencia " + competitionId));
    }

    private Match findEntityById(Long id) {
        return matchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Partido con id " + id + " no fue encontrado"));
    }
}
