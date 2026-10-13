package com.athenet.competition.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.athenet.competition.client.InstitutionClient;
import com.athenet.competition.client.SportInfo;
import com.athenet.competition.exception.ConflictException;
import com.athenet.competition.exception.ResourceNotFoundException;
import com.athenet.competition.messaging.ResultEventPublisher;
import com.athenet.competition.model.*;
import com.athenet.competition.model.enums.MatchStatus;
import com.athenet.competition.repository.*;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {
    @Mock MatchRepository matches;
    @Mock CompetitionRepository competitions;
    @Mock EnrollmentRepository enrollments;
    @Mock ResultEventPublisher publisher;
    @Mock InstitutionClient institutions;
    MatchService service;
    Match match;
    Competition competition;
    Enrollment home;
    Enrollment away;

    @BeforeEach
    void setup() {
        service = new MatchService(matches, competitions, enrollments, publisher, institutions);
        match = new Match();
        match.setId(31L);
        match.setCompetitionId(4L);
        match.setHomeTeamId(12L);
        match.setAwayTeamId(15L);
        competition = new Competition();
        competition.setSportId(1L);
        home = new Enrollment();
        away = new Enrollment();
        lenient().when(matches.findById(31L)).thenReturn(Optional.of(match));
    }

    void stubPublication() {
        when(competitions.findById(4L)).thenReturn(Optional.of(competition));
        when(enrollments.findByCompetitionIdAndTeamId(4L, 12L)).thenReturn(Optional.of(home));
        when(enrollments.findByCompetitionIdAndTeamId(4L, 15L)).thenReturn(Optional.of(away));
        when(matches.save(any(Match.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void resultAndCorrectionIncrementVersionAndPublish() {
        stubPublication();
        service.registerResult(4L, 31L, 2, 1);
        assertEquals(MatchStatus.FINISHED, match.getStatus());
        assertEquals(1, match.getVersion());
        service.registerResult(4L, 31L, 3, 1);
        assertEquals(2, match.getVersion());
        assertEquals(3, match.getHomeScore());
        assertFalse(match.isSimulated());
        verify(publisher, times(2)).publish(match, competition, home, away);
    }

    @Test
    void wrongCompetitionCannotModifyMatch() {
        assertThrows(ResourceNotFoundException.class, () -> service.registerResult(9L, 31L, 2, 1));
        assertThrows(ResourceNotFoundException.class, () -> service.simulate(9L, 31L));
        assertNull(match.getHomeScore());
        verifyNoInteractions(publisher, institutions);
        verify(matches, never()).save(any());
    }

    @Test
    void cancelledMatchAndNegativeScoreAreRejected() {
        assertThrows(ConflictException.class, () -> service.registerResult(4L, 31L, -1, 0));
        match.setStatus(MatchStatus.CANCELLED);
        assertThrows(ConflictException.class, () -> service.registerResult(4L, 31L, 2, 1));
        verifyNoInteractions(publisher);
    }

    @Test
    void footballSimulationUsesValidScoresAndPublishes() {
        stubPublication();
        when(institutions.getSport(1L)).thenReturn(new SportInfo(1L, "Fútbol"));
        service.simulate(4L, 31L);
        assertTrue(match.getHomeScore() >= 0 && match.getHomeScore() <= 5);
        assertTrue(match.getAwayScore() >= 0 && match.getAwayScore() <= 5);
        assertTrue(match.isSimulated());
        assertEquals(MatchStatus.FINISHED, match.getStatus());
        assertEquals(1, match.getVersion());
        verify(publisher).publish(match, competition, home, away);
        assertThrows(ConflictException.class, () -> service.simulate(4L, 31L));
    }

    @Test
    void basketballSimulationHasNoDraw() {
        stubPublication();
        when(institutions.getSport(1L)).thenReturn(new SportInfo(1L, "Básquetbol"));
        service.simulate(4L, 31L);
        assertTrue(match.getHomeScore() >= 50 && match.getHomeScore() <= 110);
        assertTrue(match.getAwayScore() >= 50 && match.getAwayScore() <= 110);
        assertNotEquals(match.getHomeScore(), match.getAwayScore());
        verify(publisher).publish(match, competition, home, away);
    }

    @Test
    void repeatedEventUpdatesSameMatchWithoutOverwritingResult() {
        match.setStatus(MatchStatus.FINISHED);
        match.setHomeScore(2);
        match.setAwayScore(1);
        match.setVersion(1);
        when(matches.findByEventId(57L)).thenReturn(Optional.of(match));
        java.time.LocalDate date = java.time.LocalDate.of(2026, 11, 8);
        service.upsertFromEvent(57L, "EVT-TEST-01", 4L, date, null, 12L, 15L);
        service.upsertFromEvent(57L, "EVT-TEST-01", 4L, date, null, 12L, 15L);
        verify(matches, times(2)).save(match);
        assertEquals(2, match.getHomeScore());
        assertEquals(1, match.getVersion());
        assertEquals(MatchStatus.FINISHED, match.getStatus());
        verifyNoInteractions(publisher);
    }

    @Test
    void removingCompetitionPreservesFinishedMatch() {
        match.setStatus(MatchStatus.FINISHED);
        when(matches.findByEventId(57L)).thenReturn(Optional.of(match));
        service.removeIfUnresolved(57L);
        verify(matches, never()).delete(any());
    }

    @Test
    void cancellationPersistsCancelledStatus() {
        when(matches.findByEventId(57L)).thenReturn(Optional.of(match));
        service.cancelByEventId(57L);
        assertEquals(MatchStatus.CANCELLED, match.getStatus());
        verify(matches).save(match);
    }

    @Test
    void unsupportedSportDoesNotSaveOrPublish() {
        when(competitions.findById(4L)).thenReturn(Optional.of(competition));
        when(institutions.getSport(1L)).thenReturn(new SportInfo(1L, "Tenis"));
        assertThrows(ConflictException.class, () -> service.simulate(4L, 31L));
        verify(matches, never()).save(any());
        verifyNoInteractions(publisher);
    }
}
