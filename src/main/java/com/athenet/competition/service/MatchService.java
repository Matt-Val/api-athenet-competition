package com.athenet.competition.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.athenet.competition.exception.ConflictException;
import com.athenet.competition.exception.ForbiddenException;
import com.athenet.competition.exception.ResourceNotFoundException;
import com.athenet.competition.messaging.MatchUpdatedPublisher;
import com.athenet.competition.messaging.ResultEventPublisher;
import com.athenet.competition.model.Competition;
import com.athenet.competition.model.Enrollment;
import com.athenet.competition.model.Match;
import com.athenet.competition.model.enums.CompetitionStatus;
import com.athenet.competition.model.enums.MatchRound;
import com.athenet.competition.model.enums.MatchStatus;
import com.athenet.competition.repository.CompetitionRepository;
import com.athenet.competition.repository.EnrollmentRepository;
import com.athenet.competition.repository.MatchRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchService {

    // Orden de la llave: por ronda (orden del enum) y posición
    private static final Comparator<Match> BRACKET_ORDER =
            Comparator.comparing(Match::getRound).thenComparingInt(Match::getPosition);

    private final MatchRepository matchRepository;
    private final CompetitionRepository competitionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ResultEventPublisher resultEventPublisher;
    private final MatchUpdatedPublisher matchUpdatedPublisher;

    public Match getById(Long id) {
        return findEntityById(id);
    }

    public List<Match> listByCompetition(Long competitionId) {
        return matchRepository.findByCompetitionId(competitionId).stream()
                .sorted(BRACKET_ORDER)
                .toList();
    }

    // "Mis eventos": partidos del delegado en competencias ya iniciadas (los borradores no se ven)
    public List<Match> listForDelegate(String delegateOid) {
        if (delegateOid == null) {
            return List.of();
        }
        List<Match> matches = matchRepository.findByDelegateOid(delegateOid);
        Set<Long> competitionIds = matches.stream().map(Match::getCompetitionId).collect(Collectors.toSet());
        Set<Long> visible = competitionRepository.findAllById(competitionIds).stream()
                .filter(competition -> competition.getStatus() != CompetitionStatus.DRAFT)
                .map(Competition::getId)
                .collect(Collectors.toSet());
        return matches.stream()
                .filter(match -> visible.contains(match.getCompetitionId()))
                .sorted(Comparator.comparing(Match::getDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Match::getTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    // Llave de la copa: 8 + 4 + 2 + 1 partidos vacíos, que el director programa y los resultados van llenando
    @Transactional
    public void createBracket(Long competitionId) {
        List<Match> bracket = new ArrayList<>();
        for (MatchRound round : MatchRound.values()) {
            for (int position = 1; position <= round.getMatches(); position++) {
                Match match = new Match();
                match.setCompetitionId(competitionId);
                match.setRound(round);
                match.setPosition(position);
                bracket.add(match);
            }
        }
        matchRepository.saveAll(bracket);
    }

    @Transactional
    public void deleteByCompetition(Long competitionId) {
        matchRepository.deleteByCompetitionId(competitionId);
    }

    // Para iniciar la copa: los 8 partidos de octavos tienen sus dos equipos
    public boolean isDrawComplete(Long competitionId) {
        List<Match> roundOf16 = matchRepository.findByCompetitionIdAndRound(competitionId, MatchRound.ROUND_OF_16);
        return roundOf16.size() == MatchRound.ROUND_OF_16.getMatches()
                && roundOf16.stream().allMatch(match -> match.getHomeTeamId() != null && match.getAwayTeamId() != null);
    }

    // Al iniciar la copa o editarla: (re)publica en Eventos todos sus partidos completos
    public void publishSchedule(Competition competition) {
        matchRepository.findByCompetitionId(competition.getId())
                .forEach(match -> publishIfReady(match, competition));
    }

    /*
        PUT .../matches/{matchId}: el director programa el partido (fecha, hora, lugar, delegado).
        Los equipos solo se eligen en octavos y con la copa en DRAFT (el sorteo); en las demás
        rondas los define el ganador del partido anterior.
    */
    @Transactional
    public Match updateMatch(Long competitionId, Long matchId, Match changes) {
        Match match = findCompetitionMatch(competitionId, matchId);
        Competition competition = findCompetition(competitionId);

        if (competition.getStatus() == CompetitionStatus.FINISHED) {
            throw new ConflictException("La competencia ya finalizó");
        }
        if (match.getStatus() != MatchStatus.SCHEDULED) {
            throw new ConflictException("Solo se pueden editar partidos que no han comenzado");
        }
        if ((changes.getDelegateOid() == null) != (changes.getDelegateDisplayName() == null)) {
            throw new ConflictException("El delegado requiere oid y nombre");
        }
        boolean teamsChanged = !Objects.equals(match.getHomeTeamId(), changes.getHomeTeamId())
                || !Objects.equals(match.getAwayTeamId(), changes.getAwayTeamId());
        if (teamsChanged) {
            validateDraw(competition, match, changes.getHomeTeamId(), changes.getAwayTeamId());
        }
        boolean published = isPublishable(match, competition);

        match.setDate(changes.getDate());
        match.setTime(changes.getTime());
        match.setLocation(changes.getLocation());
        match.setAddress(changes.getAddress());
        match.setHomeTeamId(changes.getHomeTeamId());
        match.setAwayTeamId(changes.getAwayTeamId());
        match.setDelegateOid(changes.getDelegateOid());
        match.setDelegateDisplayName(changes.getDelegateDisplayName());

        // Su copia ya está en Eventos: no puede quedar sin equipos, fecha, hora ni delegado
        if (published && !match.isComplete()) {
            throw new ConflictException("Un partido publicado debe mantener equipos, fecha, hora y delegado");
        }

        Match saved = matchRepository.save(match);
        publishIfReady(saved, competition);
        return saved;
    }

    // POST /api/delegate/matches/{id}/start: puede ser antes de la hora programada
    @Transactional
    public Match start(Long matchId, String delegateOid) {
        Match match = findDelegateMatch(matchId, delegateOid);
        Competition competition = findCompetition(match.getCompetitionId());

        if (competition.getStatus() != CompetitionStatus.IN_PROGRESS) {
            throw new ConflictException("La competencia no está en curso");
        }
        if (match.getStatus() != MatchStatus.SCHEDULED) {
            throw new ConflictException("El partido ya fue iniciado");
        }
        if (!match.isComplete()) {
            throw new ConflictException("El partido todavía no tiene equipos, fecha, hora y delegado");
        }

        match.setStatus(MatchStatus.IN_PROGRESS);
        return matchRepository.save(match);
    }

    /*
        PUT /api/delegate/matches/{id}/result: resultado final del delegado asignado. Si el partido
        está en juego lo finaliza y el ganador avanza en la llave; si ya terminó es una corrección
        y version sube igual. Siempre publica resultado.registrado (§7) para Ranking.
    */
    @Transactional
    public Match registerResult(Long matchId, String delegateOid, int homeScore, int awayScore) {
        Match match = findDelegateMatch(matchId, delegateOid);

        if (homeScore < 0 || awayScore < 0) {
            throw new ConflictException("El marcador no puede ser negativo");
        }
        if (homeScore == awayScore) {
            throw new ConflictException("No se admiten empates: el partido debe tener un ganador");
        }
        if (match.getStatus() == MatchStatus.SCHEDULED) {
            throw new ConflictException("Primero hay que iniciar el partido");
        }

        Long previousWinner = match.getStatus() == MatchStatus.FINISHED ? winnerOf(match) : null;
        match.setHomeScore(homeScore);
        match.setAwayScore(awayScore);
        match.setStatus(MatchStatus.FINISHED);
        match.setVersion(match.getVersion() + 1);

        Long winner = winnerOf(match);
        if (!winner.equals(previousWinner)) {
            advanceWinner(match, previousWinner != null, winner);
        }

        Match saved = matchRepository.save(match);
        publishResult(saved);
        return saved;
    }

    /*
        El ganador pasa al partido ⌈position/2⌉ de la ronda siguiente: de local si la posición
        es impar, de visita si es par. Si cambia el ganador de una corrección, el partido siguiente
        no puede haber comenzado. Ganar la final termina la copa.
    */
    private void advanceWinner(Match match, boolean correction, Long winner) {
        Competition competition = findCompetition(match.getCompetitionId());
        MatchRound nextRound = match.getRound().next();

        if (nextRound == null) {
            competition.setStatus(CompetitionStatus.FINISHED);
            competitionRepository.save(competition);
            return;
        }

        Match next = matchRepository
                .findByCompetitionIdAndRoundAndPosition(match.getCompetitionId(), nextRound, (match.getPosition() + 1) / 2)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el partido siguiente de la llave"));
        if (correction && next.getStatus() != MatchStatus.SCHEDULED) {
            throw new ConflictException("El partido siguiente ya comenzó: no se puede cambiar el ganador");
        }

        if (match.getPosition() % 2 == 1) {
            next.setHomeTeamId(winner);
        } else {
            next.setAwayTeamId(winner);
        }
        publishIfReady(matchRepository.save(next), competition);
    }

    private void validateDraw(Competition competition, Match match, Long homeTeamId, Long awayTeamId) {
        if (match.getRound() != MatchRound.ROUND_OF_16 || competition.getStatus() != CompetitionStatus.DRAFT) {
            throw new ConflictException("Los equipos solo se eligen en octavos y antes de iniciar la copa");
        }
        if (homeTeamId != null && homeTeamId.equals(awayTeamId)) {
            throw new ConflictException("Un equipo no puede enfrentarse a sí mismo");
        }

        List<Match> otherDraws = matchRepository.findByCompetitionIdAndRound(competition.getId(), MatchRound.ROUND_OF_16)
                .stream()
                .filter(other -> !other.getId().equals(match.getId()))
                .toList();
        for (Long teamId : Arrays.asList(homeTeamId, awayTeamId)) {
            if (teamId == null) {
                continue;
            }
            if (!enrollmentRepository.existsByCompetitionIdAndTeamId(competition.getId(), teamId)) {
                throw new ConflictException("El equipo " + teamId + " no está inscrito en la competencia");
            }
            if (otherDraws.stream().anyMatch(other -> teamId.equals(other.getHomeTeamId()) || teamId.equals(other.getAwayTeamId()))) {
                throw new ConflictException("El equipo " + teamId + " ya está en otro partido de octavos");
            }
        }
    }

    // Copia para Eventos: solo partidos completos de competencias que ya no son borrador
    private boolean isPublishable(Match match, Competition competition) {
        return competition.getStatus() != CompetitionStatus.DRAFT && match.isComplete();
    }

    private void publishIfReady(Match match, Competition competition) {
        if (!isPublishable(match, competition)) {
            return;
        }
        Enrollment home = findEnrollment(match.getCompetitionId(), match.getHomeTeamId());
        Enrollment away = findEnrollment(match.getCompetitionId(), match.getAwayTeamId());
        matchUpdatedPublisher.publish(match, competition, home, away);
    }

    private void publishResult(Match match) {
        Competition competition = findCompetition(match.getCompetitionId());
        Enrollment home = findEnrollment(match.getCompetitionId(), match.getHomeTeamId());
        Enrollment away = findEnrollment(match.getCompetitionId(), match.getAwayTeamId());

        resultEventPublisher.publish(match, competition, home, away);
    }

    // Sin empates: siempre hay ganador cuando hay marcador
    private static Long winnerOf(Match match) {
        return match.getHomeScore() > match.getAwayScore() ? match.getHomeTeamId() : match.getAwayTeamId();
    }

    private Match findDelegateMatch(Long matchId, String delegateOid) {
        Match match = findEntityById(matchId);
        if (delegateOid == null || !delegateOid.equalsIgnoreCase(match.getDelegateOid())) {
            throw new ForbiddenException("Solo el delegado asignado puede gestionar el partido " + matchId);
        }
        return match;
    }

    private Match findCompetitionMatch(Long competitionId, Long matchId) {
        Match match = findEntityById(matchId);
        if (!competitionId.equals(match.getCompetitionId())) {
            throw new ResourceNotFoundException("El partido " + matchId + " no pertenece a la competencia " + competitionId);
        }
        return match;
    }

    private Competition findCompetition(Long competitionId) {
        return competitionRepository.findById(competitionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Competencia con id " + competitionId + " no fue encontrada"));
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
