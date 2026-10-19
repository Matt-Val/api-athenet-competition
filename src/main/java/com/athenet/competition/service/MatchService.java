package com.athenet.competition.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.athenet.competition.exception.ConflictException;
import com.athenet.competition.exception.ForbiddenException;
import com.athenet.competition.exception.ResourceNotFoundException;
import com.athenet.competition.messaging.ResultEventPublisher;
import com.athenet.competition.messaging.ScheduleEventPublisher;
import com.athenet.competition.model.Competition;
import com.athenet.competition.model.Enrollment;
import com.athenet.competition.model.Match;
import com.athenet.competition.model.TeamProgress;
import com.athenet.competition.model.enums.CompetitionStatus;
import com.athenet.competition.model.enums.MatchRound;
import com.athenet.competition.model.enums.MatchStatus;
import com.athenet.competition.model.enums.TeamOutcome;
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

    // Perfil del equipo: los más recientes primero, los que no tienen fecha al final
    private static final Comparator<Match> LATEST_FIRST =
            Comparator.comparing(Match::getDate, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(Match::getTime, Comparator.nullsLast(Comparator.reverseOrder()));

    private final MatchRepository matchRepository;
    private final CompetitionRepository competitionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ResultEventPublisher resultEventPublisher;
    private final ScheduleEventPublisher scheduleEventPublisher;

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

    // Partido del perfil del equipo, con su competencia y los dos inscritos (null si aún no hay rival)
    public record TeamMatch(Match match, Competition competition, Enrollment home, Enrollment away) {
    }

    // Perfil público del equipo: sus partidos en competencias ya iniciadas (los borradores no se ven)
    public List<TeamMatch> listForTeam(Long teamId, MatchStatus status, int limit) {
        List<Match> teamMatches = matchRepository.findByTeamId(teamId).stream()
                .filter(match -> status == null || match.getStatus() == status)
                .toList();
        Set<Long> competitionIds = teamMatches.stream().map(Match::getCompetitionId).collect(Collectors.toSet());
        Map<Long, Competition> visible = competitionRepository.findAllById(competitionIds).stream()
                .filter(competition -> competition.getStatus() != CompetitionStatus.DRAFT)
                .collect(Collectors.toMap(Competition::getId, Function.identity()));
        Map<Long, Map<Long, Enrollment>> enrolled = enrollmentRepository.findByCompetitionIdIn(visible.keySet()).stream()
                .collect(Collectors.groupingBy(Enrollment::getCompetitionId,
                        Collectors.toMap(Enrollment::getTeamId, Function.identity())));
        return teamMatches.stream()
                .filter(match -> visible.containsKey(match.getCompetitionId()))
                .sorted(LATEST_FIRST)
                .limit(limit)
                .map(match -> {
                    Map<Long, Enrollment> teams = enrolled.get(match.getCompetitionId());
                    return new TeamMatch(match, visible.get(match.getCompetitionId()),
                            teams.get(match.getHomeTeamId()), teams.get(match.getAwayTeamId()));
                })
                .toList();
    }

    // Partido de la llave publica, con su competencia y los dos inscritos (null si aun no hay equipo)
    public record FixtureMatch(Match match, Competition competition, Enrollment home, Enrollment away) {
    }

    /*
        Llave publica de una competencia, en orden de cuadro. No reusa listByCompetition
        porque ese lo comparte el controller de admin, que necesita ver la llave en DRAFT
        para hacer el sorteo.
    */
    public List<FixtureMatch> listFixture(Long competitionId) {
        Competition competition = findCompetition(competitionId);
        if (competition.getStatus() == CompetitionStatus.DRAFT) {
            throw new ResourceNotFoundException(
                    "Competencia con id " + competitionId + " no fue encontrada");
        }
        Map<Long, Enrollment> enrolled = enrollmentRepository.findByCompetitionId(competitionId).stream()
                .collect(Collectors.toMap(Enrollment::getTeamId, Function.identity()));
        return matchRepository.findByCompetitionId(competitionId).stream()
                .sorted(BRACKET_ORDER)
                .map(match -> new FixtureMatch(match, competition,
                        enrolled.get(match.getHomeTeamId()), enrolled.get(match.getAwayTeamId())))
                .toList();
    }

    // Cómo le fue al equipo en cada competencia, por competitionId
    public Map<Long, TeamProgress> progressByCompetition(Long teamId) {
        return matchRepository.findByTeamId(teamId).stream()
                .collect(Collectors.groupingBy(Match::getCompetitionId,
                        Collectors.collectingAndThen(Collectors.toList(), matches -> progressOf(teamId, matches))));
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
        matchRepository.findByCompetitionId(competitionId).forEach(scheduleEventPublisher::cancel);
        matchRepository.deleteByCompetitionId(competitionId);
    }

    // Para iniciar la copa: los 8 partidos de octavos tienen sus dos equipos
    public boolean isDrawComplete(Long competitionId) {
        List<Match> roundOf16 = matchRepository.findByCompetitionIdAndRound(competitionId, MatchRound.ROUND_OF_16);
        return roundOf16.size() == MatchRound.ROUND_OF_16.getMatches()
                && roundOf16.stream().allMatch(match -> match.getHomeTeamId() != null && match.getAwayTeamId() != null);
    }

    /*
        PUT .../matches/{matchId}: el director programa el partido (fecha, hora, lugar, delegado).
        Los equipos solo se eligen en octavos y con la copa en DRAFT (el sorteo); en las demás
        rondas los define el ganador del partido anterior.
    */
    @Transactional
    public Match updateMatch(Long competitionId, Long matchId, Match changes) {
        lockCompetition(competitionId);
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

        boolean scheduleChanged = !scheduleFields(match).equals(scheduleFields(changes));
        if (scheduleChanged) scheduleEventPublisher.cancel(match);
        match.setDate(changes.getDate());
        match.setTime(changes.getTime());
        match.setEndDate(changes.getEndDate());
        match.setEndTime(changes.getEndTime());
        match.setLocation(changes.getLocation());
        match.setAddress(changes.getAddress());
        match.setHomeTeamId(changes.getHomeTeamId());
        match.setAwayTeamId(changes.getAwayTeamId());
        match.setDelegateOid(changes.getDelegateOid());
        match.setDelegateDisplayName(changes.getDelegateDisplayName());
        if (scheduleChanged) match.setScheduleConfirmed(false);
        return matchRepository.save(match);
    }

    @Transactional
    public Match confirmSchedule(Long competitionId, Long matchId) {
        lockCompetition(competitionId);
        Match match = findCompetitionMatch(competitionId, matchId);
        if (match.getStatus() != MatchStatus.SCHEDULED) {
            throw new ConflictException("Solo se confirma un encuentro que no ha comenzado");
        }
        if (!match.isComplete() || match.getEndDate() == null || match.getEndTime() == null
                || !java.time.LocalDateTime.of(match.getEndDate(), match.getEndTime())
                    .isAfter(java.time.LocalDateTime.of(match.getDate(), match.getTime()))) {
            throw new ConflictException("El encuentro requiere equipos, delegado e inicio y término válidos");
        }
        if (match.isScheduleConfirmed()) return match;
        Competition competition = findCompetition(competitionId);
        if (competition.getStatus() == CompetitionStatus.FINISHED) {
            throw new ConflictException("La competencia ya finalizó");
        }
        if (competition.getCompetitionImg() == null || competition.getCompetitionImg().isBlank()) {
            throw new ConflictException("La competencia requiere una portada para publicar el encuentro");
        }
        match.setScheduleConfirmed(true);
        match.setScheduleVersion(match.getScheduleVersion() + 1);
        Match saved = matchRepository.save(match);
        scheduleEventPublisher.publish(saved, competition,
                findEnrollment(competitionId, match.getHomeTeamId()), findEnrollment(competitionId, match.getAwayTeamId()));
        return saved;
    }

    @Transactional
    public void refreshConfirmedSchedules(Competition competition) {
        for (Match match : matchRepository.findByCompetitionId(competition.getId())) {
            if (!match.isScheduleConfirmed()) continue;
            match.setScheduleVersion(match.getScheduleVersion() + 1);
            scheduleEventPublisher.publish(match,competition,
                    findEnrollment(competition.getId(),match.getHomeTeamId()),
                    findEnrollment(competition.getId(),match.getAwayTeamId()));
        }
    }

    private static List<Object> scheduleFields(Match m) {
        return Arrays.asList(m.getDate(), m.getTime(), m.getEndDate(), m.getEndTime(), m.getLocation(), m.getAddress(),
                m.getHomeTeamId(), m.getAwayTeamId(), m.getDelegateOid(), m.getDelegateDisplayName());
    }

    // POST /api/delegate/matches/{id}/start: puede ser antes de la hora programada
    @Transactional
    public Match start(Long matchId, String delegateOid) {
        lockMatchCompetition(matchId);
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
        if (!match.isScheduleConfirmed()) {
            throw new ConflictException("El director debe confirmar la programación del encuentro antes de iniciarlo");
        }

        match.setStatus(MatchStatus.IN_PROGRESS);
        return matchRepository.save(match);
    }

    /*
        PUT /api/delegate/matches/{id}/result: resultado final del delegado asignado. Si el partido
        está en juego lo finaliza y el ganador avanza en la llave; si ya terminó es una corrección
        y version sube igual. Siempre publica resultado.registrado (§7) para Notificaciones.
    */
    @Transactional
    public Match registerResult(Long matchId, String delegateOid, int homeScore, int awayScore) {
        lockMatchCompetition(matchId);
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

        if (match.getStatus() == MatchStatus.FINISHED
                && Objects.equals(match.getHomeScore(), homeScore)
                && Objects.equals(match.getAwayScore(), awayScore)) {
            return match;
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
        scheduleEventPublisher.cancel(next);
        next.setScheduleConfirmed(false);
        matchRepository.save(next);
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

    private void publishResult(Match match) {
        Competition competition = findCompetition(match.getCompetitionId());
        Enrollment home = findEnrollment(match.getCompetitionId(), match.getHomeTeamId());
        Enrollment away = findEnrollment(match.getCompetitionId(), match.getAwayTeamId());

        resultEventPublisher.publish(match, competition, home, away);
    }

    private void lockMatchCompetition(Long matchId) {
        Long competitionId = matchRepository.findCompetitionIdById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Encuentro no encontrado"));
        lockCompetition(competitionId);
    }

    private void lockCompetition(Long competitionId) {
        competitionRepository.findLockedById(competitionId)
                .orElseThrow(() -> new ResourceNotFoundException("Competencia no encontrada"));
    }

    // Sin empates: siempre hay ganador cuando hay marcador
    private static Long winnerOf(Match match) {
        return match.getHomeScore() > match.getAwayScore() ? match.getHomeTeamId() : match.getAwayTeamId();
    }

    /*
        Ronda más lejana a la que llegó y partidos terminados. Una derrota lo elimina (es copa)
        y ganar la final lo deja campeón; si no, sigue en carrera.
    */
    private static TeamProgress progressOf(Long teamId, List<Match> matches) {
        MatchRound round = matches.stream().map(Match::getRound).max(Comparator.naturalOrder()).orElseThrow();
        List<Match> finished = matches.stream().filter(match -> match.getStatus() == MatchStatus.FINISHED).toList();
        int won = (int) finished.stream().filter(match -> teamId.equals(winnerOf(match))).count();
        int lost = finished.size() - won;

        TeamOutcome outcome = TeamOutcome.ACTIVE;
        if (lost > 0) {
            outcome = TeamOutcome.ELIMINATED;
        } else if (finished.stream().anyMatch(match -> match.getRound() == MatchRound.FINAL)) {
            outcome = TeamOutcome.CHAMPION;
        }
        return new TeamProgress(round, outcome, finished.size(), won, lost);
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
