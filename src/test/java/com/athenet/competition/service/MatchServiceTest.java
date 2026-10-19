package com.athenet.competition.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.athenet.competition.exception.ConflictException;
import com.athenet.competition.exception.ForbiddenException;
import com.athenet.competition.exception.ResourceNotFoundException;
import com.athenet.competition.messaging.ResultEventPublisher;
import com.athenet.competition.messaging.ScheduleEventPublisher;
import com.athenet.competition.model.*;
import com.athenet.competition.model.enums.*;
import com.athenet.competition.repository.*;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {
    static final String DELEGATE = "3f2b9c1e-5a7d-4c1b-9e2f-1d0a8b6c4e21";

    @Mock MatchRepository matches;
    @Mock CompetitionRepository competitions;
    @Mock EnrollmentRepository enrollments;
    @Mock ResultEventPublisher resultPublisher;
    @Mock ScheduleEventPublisher schedulePublisher;
    MatchService service;
    Competition competition;
    // Octavos, posición 3: su ganador pasa a cuartos, posición 2, de local
    Match match;
    Match next;
    Enrollment home;
    Enrollment away;

    @BeforeEach
    void setup() {
        service = new MatchService(matches, competitions, enrollments, resultPublisher, schedulePublisher);
        competition = new Competition();
        competition.setId(4L);
        competition.setCompetitionImg("https://example.test/cup.png");
        competition.setType(CompetitionType.CUP);
        competition.setStatus(CompetitionStatus.IN_PROGRESS);
        match = match(31L, MatchRound.ROUND_OF_16, 3, 12L, 15L);
        match.setStatus(MatchStatus.IN_PROGRESS);
        next = match(40L, MatchRound.QUARTER_FINAL, 2, null, null);
        home = new Enrollment();
        away = new Enrollment();
        lenient().when(matches.findById(31L)).thenReturn(Optional.of(match));
        lenient().when(matches.findById(40L)).thenReturn(Optional.of(next));
        lenient().when(competitions.findLockedById(4L)).thenReturn(Optional.of(competition));
        lenient().when(matches.findCompetitionIdById(anyLong())).thenReturn(Optional.of(4L));
        lenient().when(competitions.findById(4L)).thenReturn(Optional.of(competition));
        lenient().when(matches.findByCompetitionIdAndRoundAndPosition(4L, MatchRound.QUARTER_FINAL, 2))
                .thenReturn(Optional.of(next));
        lenient().when(matches.save(any(Match.class))).thenAnswer(call -> call.getArgument(0));
        lenient().when(enrollments.findByCompetitionIdAndTeamId(4L, 12L)).thenReturn(Optional.of(home));
        lenient().when(enrollments.findByCompetitionIdAndTeamId(4L, 15L)).thenReturn(Optional.of(away));
    }

    static Match match(Long id, MatchRound round, int position, Long homeTeamId, Long awayTeamId) {
        Match match = new Match();
        match.setId(id);
        match.setCompetitionId(4L);
        match.setRound(round);
        match.setPosition(position);
        match.setHomeTeamId(homeTeamId);
        match.setAwayTeamId(awayTeamId);
        match.setDate(LocalDate.of(2026, 11, 8));
        match.setTime(LocalTime.of(18, 0));
        match.setEndDate(match.getDate());
        match.setEndTime(LocalTime.of(20, 0));
        match.setScheduleConfirmed(true);
        match.setDelegateOid(DELEGATE);
        match.setDelegateDisplayName("Delegado de prueba");
        return match;
    }

    static Match changesFrom(Match match) {
        Match changes = new Match();
        changes.setDate(match.getDate());
        changes.setTime(match.getTime());
        changes.setEndDate(match.getEndDate());
        changes.setEndTime(match.getEndTime());
        changes.setLocation(match.getLocation());
        changes.setAddress(match.getAddress());
        changes.setHomeTeamId(match.getHomeTeamId());
        changes.setAwayTeamId(match.getAwayTeamId());
        changes.setDelegateOid(match.getDelegateOid());
        changes.setDelegateDisplayName(match.getDelegateDisplayName());
        return changes;
    }

    @Test
    @SuppressWarnings("unchecked")
    void bracketHasFifteenEmptyMatches() {
        service.createBracket(4L);
        ArgumentCaptor<List<Match>> captor = ArgumentCaptor.forClass(List.class);
        verify(matches).saveAll(captor.capture());
        List<Match> bracket = captor.getValue();
        assertEquals(15, bracket.size());
        assertEquals(8, bracket.stream().filter(m -> m.getRound() == MatchRound.ROUND_OF_16).count());
        assertEquals(1, bracket.stream().filter(m -> m.getRound() == MatchRound.FINAL).count());
        assertTrue(bracket.stream().allMatch(m -> m.getHomeTeamId() == null && m.getStatus() == MatchStatus.SCHEDULED));
    }

    @Test
    void finishingAdvancesWinnerAndPublishesResult() {
        service.registerResult(31L, DELEGATE, 2, 1);
        assertEquals(MatchStatus.FINISHED, match.getStatus());
        assertEquals(1, match.getVersion());
        assertEquals(12L, next.getHomeTeamId());
        verify(resultPublisher).publish(match, competition, home, away);
    }

    @Test
    void confirmationIsSeparateAndIdempotentAndChangesRequireReconfirmation() {
        match.setStatus(MatchStatus.SCHEDULED);
        match.setScheduleConfirmed(false);
        service.confirmSchedule(4L,31L);
        service.confirmSchedule(4L,31L);
        assertTrue(match.isScheduleConfirmed());
        assertEquals(1,match.getScheduleVersion());
        verify(schedulePublisher,times(1)).publish(match,competition,home,away);
        Match changes = changesFrom(match);
        changes.setLocation("Otro estadio");
        service.updateMatch(4L,31L,changes);
        assertFalse(match.isScheduleConfirmed());
        assertThrows(ConflictException.class,() -> service.start(31L,DELEGATE));
        service.confirmSchedule(4L,31L);
        assertEquals(2,match.getScheduleVersion());
        verify(schedulePublisher,times(2)).publish(match,competition,home,away);
    }

    @Test
    void confirmationRequiresValidExplicitEnd() {
        match.setStatus(MatchStatus.SCHEDULED);
        match.setScheduleConfirmed(false);
        match.setEndDate(null);
        assertThrows(ConflictException.class,() -> service.confirmSchedule(4L,31L));
        match.setEndDate(match.getDate());
        match.setEndTime(match.getTime());
        assertThrows(ConflictException.class,() -> service.confirmSchedule(4L,31L));
        verifyNoInteractions(schedulePublisher);
    }

    @Test
    void identicalResultIsIdempotent() {
        service.registerResult(31L, DELEGATE, 2, 1);
        service.registerResult(31L, DELEGATE, 2, 1);
        assertEquals(1, match.getVersion());
        verify(resultPublisher, times(1)).publish(match, competition, home, away);
    }

    @Test
    void advancedWinnerCompletesNextMatch() {
        next.setAwayTeamId(20L);
        service.registerResult(31L, DELEGATE, 0, 3);
        assertEquals(15L, next.getHomeTeamId());
        assertEquals(20L, next.getAwayTeamId());
        verify(matches).save(next);
    }

    @Test
    void drawsAreRejected() {
        assertThrows(ConflictException.class, () -> service.registerResult(31L, DELEGATE, 1, 1));
        verify(matches, never()).save(any());
        verifyNoInteractions(resultPublisher);
    }

    @Test
    void onlyAssignedDelegateManagesMatch() {
        assertThrows(ForbiddenException.class, () -> service.start(31L, "otro-delegado"));
        assertThrows(ForbiddenException.class, () -> service.registerResult(31L, "otro-delegado", 2, 1));
        assertThrows(ForbiddenException.class, () -> service.registerResult(31L, null, 2, 1));
        assertNull(match.getHomeScore());
        verifyNoInteractions(resultPublisher);
    }

    @Test
    void matchMustStartBeforeItsResult() {
        match.setStatus(MatchStatus.SCHEDULED);
        assertThrows(ConflictException.class, () -> service.registerResult(31L, DELEGATE, 2, 1));
        service.start(31L, DELEGATE);
        assertEquals(MatchStatus.IN_PROGRESS, match.getStatus());
        assertThrows(ConflictException.class, () -> service.start(31L, DELEGATE));
    }

    @Test
    void startRequiresCupInProgressAndCompleteMatch() {
        match.setStatus(MatchStatus.SCHEDULED);
        competition.setStatus(CompetitionStatus.DRAFT);
        assertThrows(ConflictException.class, () -> service.start(31L, DELEGATE));
        competition.setStatus(CompetitionStatus.IN_PROGRESS);
        match.setDate(null);
        assertThrows(ConflictException.class, () -> service.start(31L, DELEGATE));
        assertEquals(MatchStatus.SCHEDULED, match.getStatus());
    }

    @Test
    void correctionWithSameWinnerOnlyUpdatesScore() {
        service.registerResult(31L, DELEGATE, 2, 1);
        service.registerResult(31L, DELEGATE, 3, 1);
        assertEquals(2, match.getVersion());
        assertEquals(3, match.getHomeScore());
        assertEquals(12L, next.getHomeTeamId());
        verify(resultPublisher, times(2)).publish(match, competition, home, away);
    }

    @Test
    void correctionChangingWinnerReplacesTeamInNextMatch() {
        service.registerResult(31L, DELEGATE, 2, 1);
        service.registerResult(31L, DELEGATE, 0, 1);
        assertEquals(15L, next.getHomeTeamId());
    }

    @Test
    void correctionCannotChangeWinnerOnceNextMatchStarted() {
        service.registerResult(31L, DELEGATE, 2, 1);
        next.setStatus(MatchStatus.IN_PROGRESS);
        assertThrows(ConflictException.class, () -> service.registerResult(31L, DELEGATE, 0, 1));
        assertEquals(12L, next.getHomeTeamId());
        verify(resultPublisher, times(1)).publish(any(), any(), any(), any());
    }

    @Test
    void winningTheFinalFinishesTheCup() {
        Match last = match(50L, MatchRound.FINAL, 1, 12L, 15L);
        last.setStatus(MatchStatus.IN_PROGRESS);
        when(matches.findById(50L)).thenReturn(Optional.of(last));
        service.registerResult(50L, DELEGATE, 4, 2);
        assertEquals(CompetitionStatus.FINISHED, competition.getStatus());
        verify(competitions).save(competition);
    }

    @Test
    void directorDrawsRoundOf16WithEnrolledTeams() {
        competition.setStatus(CompetitionStatus.DRAFT);
        match.setStatus(MatchStatus.SCHEDULED);
        Match changes = changesFrom(match);
        match.setHomeTeamId(null);
        match.setAwayTeamId(null);
        when(matches.findByCompetitionIdAndRound(4L, MatchRound.ROUND_OF_16))
                .thenReturn(List.of(match, match(32L, MatchRound.ROUND_OF_16, 4, 20L, 21L)));
        when(enrollments.existsByCompetitionIdAndTeamId(4L, 12L)).thenReturn(true);
        when(enrollments.existsByCompetitionIdAndTeamId(4L, 15L)).thenReturn(true);

        service.updateMatch(4L, 31L, changes);

        assertEquals(12L, match.getHomeTeamId());
        assertEquals(15L, match.getAwayTeamId());
    }

    @Test
    void teamCannotBeDrawnTwice() {
        competition.setStatus(CompetitionStatus.DRAFT);
        match.setStatus(MatchStatus.SCHEDULED);
        Match changes = changesFrom(match);
        match.setHomeTeamId(null);
        when(matches.findByCompetitionIdAndRound(4L, MatchRound.ROUND_OF_16))
                .thenReturn(List.of(match, match(32L, MatchRound.ROUND_OF_16, 4, 12L, 21L)));
        when(enrollments.existsByCompetitionIdAndTeamId(4L, 12L)).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.updateMatch(4L, 31L, changes));
        verify(matches, never()).save(any());
    }

    @Test
    void teamsOnlyChangeInRoundOf16BeforeTheCupStarts() {
        Match quarterChanges = changesFrom(next);
        quarterChanges.setHomeTeamId(12L);
        assertThrows(ConflictException.class, () -> service.updateMatch(4L, 40L, quarterChanges));

        match.setStatus(MatchStatus.SCHEDULED);
        Match roundOf16Changes = changesFrom(match);
        roundOf16Changes.setAwayTeamId(20L);
        assertThrows(ConflictException.class, () -> service.updateMatch(4L, 31L, roundOf16Changes));
        verify(matches, never()).save(any());
    }

    @Test
    void editingScheduledMatchOfStartedCupSavesIt() {
        match.setStatus(MatchStatus.SCHEDULED);
        Match changes = changesFrom(match);
        changes.setLocation("Estadio Nacional");
        service.updateMatch(4L, 31L, changes);
        assertEquals("Estadio Nacional", match.getLocation());
        verify(matches).save(match);
    }

    @Test
    void startedOrForeignMatchCannotBeEdited() {
        assertThrows(ConflictException.class, () -> service.updateMatch(4L, 31L, changesFrom(match)));
        assertThrows(ResourceNotFoundException.class, () -> service.updateMatch(9L, 31L, changesFrom(match)));
        verify(matches, never()).save(any());
    }

    @Test
    void delegateOnlySeesMatchesOfStartedCompetitions() {
        Competition draft = new Competition();
        draft.setId(5L);
        Match hidden = match(60L, MatchRound.ROUND_OF_16, 1, 30L, 31L);
        hidden.setCompetitionId(5L);
        when(matches.findByDelegateOid(DELEGATE)).thenReturn(List.of(match, hidden));
        when(competitions.findAllById(any())).thenReturn(List.of(competition, draft));
        assertEquals(List.of(match), service.listForDelegate(DELEGATE));
    }

    @Test
    void teamSeesItsLatestMatchesWithBothTeams() {
        Competition draft = new Competition();
        draft.setId(5L);
        Match hidden = match(60L, MatchRound.ROUND_OF_16, 1, 12L, 31L);
        hidden.setCompetitionId(5L);
        next.setHomeTeamId(12L);
        next.setDate(LocalDate.of(2026, 11, 15));
        home.setId(1L);
        home.setCompetitionId(4L);
        home.setTeamId(12L);
        away.setId(2L);
        away.setCompetitionId(4L);
        away.setTeamId(15L);
        when(matches.findByTeamId(12L)).thenReturn(List.of(match, hidden, next));
        when(competitions.findAllById(any())).thenReturn(List.of(competition, draft));
        when(enrollments.findByCompetitionIdIn(any())).thenReturn(List.of(home, away));

        List<MatchService.TeamMatch> latest = service.listForTeam(12L, null, 5);
        assertEquals(List.of(next, match), latest.stream().map(MatchService.TeamMatch::match).toList());
        assertNull(latest.get(0).away());
        assertEquals(new MatchService.TeamMatch(match, competition, home, away), latest.get(1));

        assertEquals(List.of(next), service.listForTeam(12L, null, 1).stream().map(MatchService.TeamMatch::match).toList());
        assertEquals(List.of(match), service.listForTeam(12L, MatchStatus.IN_PROGRESS, 5).stream()
                .map(MatchService.TeamMatch::match).toList());
    }

    @Test
    void progressShowsHowFarTheTeamWentInEachCompetition() {
        match.setStatus(MatchStatus.FINISHED);
        match.setHomeScore(2);
        match.setAwayScore(1);
        next.setHomeTeamId(12L);
        Match lostQuarter = finished(70L, 6L, MatchRound.QUARTER_FINAL, 20L, 12L, 3, 0);
        Match wonFinal = finished(80L, 7L, MatchRound.FINAL, 12L, 21L, 1, 0);
        when(matches.findByTeamId(12L)).thenReturn(List.of(match, next, lostQuarter, wonFinal));

        Map<Long, TeamProgress> progress = service.progressByCompetition(12L);
        assertEquals(new TeamProgress(MatchRound.QUARTER_FINAL, TeamOutcome.ACTIVE, 1, 1, 0), progress.get(4L));
        assertEquals(new TeamProgress(MatchRound.QUARTER_FINAL, TeamOutcome.ELIMINATED, 1, 0, 1), progress.get(6L));
        assertEquals(new TeamProgress(MatchRound.FINAL, TeamOutcome.CHAMPION, 1, 1, 0), progress.get(7L));
    }

    static Match finished(Long id, Long competitionId, MatchRound round, Long homeTeamId, Long awayTeamId,
            int homeScore, int awayScore) {
        Match finished = match(id, round, 1, homeTeamId, awayTeamId);
        finished.setCompetitionId(competitionId);
        finished.setStatus(MatchStatus.FINISHED);
        finished.setHomeScore(homeScore);
        finished.setAwayScore(awayScore);
        return finished;
    }
}
