package com.athenet.competition.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
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
import com.athenet.competition.messaging.MatchUpdatedPublisher;
import com.athenet.competition.messaging.ResultEventPublisher;
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
    @Mock MatchUpdatedPublisher matchPublisher;
    MatchService service;
    Competition competition;
    // Octavos, posición 3: su ganador pasa a cuartos, posición 2, de local
    Match match;
    Match next;
    Enrollment home;
    Enrollment away;

    @BeforeEach
    void setup() {
        service = new MatchService(matches, competitions, enrollments, resultPublisher, matchPublisher);
        competition = new Competition();
        competition.setId(4L);
        competition.setType(CompetitionType.CUP);
        competition.setStatus(CompetitionStatus.IN_PROGRESS);
        match = match(31L, MatchRound.ROUND_OF_16, 3, 12L, 15L);
        match.setStatus(MatchStatus.IN_PROGRESS);
        next = match(40L, MatchRound.QUARTER_FINAL, 2, null, null);
        home = new Enrollment();
        away = new Enrollment();
        lenient().when(matches.findById(31L)).thenReturn(Optional.of(match));
        lenient().when(matches.findById(40L)).thenReturn(Optional.of(next));
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
        match.setDelegateOid(DELEGATE);
        match.setDelegateDisplayName("Delegado de prueba");
        return match;
    }

    static Match changesFrom(Match match) {
        Match changes = new Match();
        changes.setDate(match.getDate());
        changes.setTime(match.getTime());
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
        // Cuartos todavía no tiene rival: no llega a Eventos
        verifyNoInteractions(matchPublisher);
    }

    @Test
    void advancedWinnerCompletesNextMatchAndPublishesIt() {
        Enrollment rival = new Enrollment();
        next.setAwayTeamId(20L);
        when(enrollments.findByCompetitionIdAndTeamId(4L, 20L)).thenReturn(Optional.of(rival));
        service.registerResult(31L, DELEGATE, 0, 3);
        assertEquals(15L, next.getHomeTeamId());
        verify(matchPublisher).publish(next, competition, away, rival);
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
        // Borrador: nada llega a Eventos
        verifyNoInteractions(matchPublisher);
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
    void editingScheduledMatchOfStartedCupRepublishesIt() {
        match.setStatus(MatchStatus.SCHEDULED);
        Match changes = changesFrom(match);
        changes.setLocation("Estadio Nacional");
        service.updateMatch(4L, 31L, changes);
        assertEquals("Estadio Nacional", match.getLocation());
        verify(matchPublisher).publish(match, competition, home, away);
    }

    @Test
    void publishedMatchCannotLoseItsSchedule() {
        match.setStatus(MatchStatus.SCHEDULED);
        Match changes = changesFrom(match);
        changes.setDate(null);
        assertThrows(ConflictException.class, () -> service.updateMatch(4L, 31L, changes));
        verify(matches, never()).save(any());
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
}
