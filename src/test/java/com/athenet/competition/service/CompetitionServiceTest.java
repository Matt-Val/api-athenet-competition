package com.athenet.competition.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.athenet.competition.client.InstitutionClient;
import com.athenet.competition.client.SportInfo;
import com.athenet.competition.exception.ConflictException;
import com.athenet.competition.exception.UpstreamServiceException;
import com.athenet.competition.model.Competition;
import com.athenet.competition.model.Enrollment;
import com.athenet.competition.model.TeamProgress;
import com.athenet.competition.model.enums.CompetitionStatus;
import com.athenet.competition.model.enums.CompetitionType;
import com.athenet.competition.model.enums.MatchRound;
import com.athenet.competition.model.enums.TeamOutcome;
import com.athenet.competition.repository.*;

class CompetitionServiceTest {
    final CompetitionRepository repository = mock(CompetitionRepository.class);
    final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
    final InstitutionClient institutions = mock(InstitutionClient.class);
    final MatchService matchService = mock(MatchService.class);
    final CompetitionService service = new CompetitionService(repository, enrollments, institutions, matchService);

    Competition existing() {
        Competition competition = new Competition();
        competition.setId(4L);
        competition.setSportId(1L);
        when(repository.findLockedById(4L)).thenReturn(Optional.of(competition));
        return competition;
    }

    Competition cup() {
        Competition competition = new Competition();
        competition.setType(CompetitionType.CUP);
        competition.setSportId(1L);
        return competition;
    }

    @Test void teamCompetitionsSkipDraftsAndComeLatestFirst() {
        Competition older = new Competition();
        older.setId(4L);
        older.setStatus(CompetitionStatus.FINISHED);
        older.setStartDate(LocalDate.of(2026, 6, 1));
        Competition newer = new Competition();
        newer.setId(5L);
        newer.setStatus(CompetitionStatus.IN_PROGRESS);
        newer.setStartDate(LocalDate.of(2026, 10, 1));
        Competition draft = new Competition();
        draft.setId(6L);
        when(enrollments.findByTeamId(12L)).thenReturn(List.of(4L, 5L, 6L).stream()
                .map(id -> new Enrollment(null, id, 12L, 1L, "Halcones FC", null))
                .toList());
        when(repository.findAllById(List.of(4L, 5L, 6L))).thenReturn(List.of(older, draft, newer));
        TeamProgress progress = new TeamProgress(MatchRound.SEMI_FINAL, TeamOutcome.ELIMINATED, 3, 2, 1);
        when(matchService.progressByCompetition(12L)).thenReturn(Map.of(4L, progress));

        List<CompetitionService.TeamCompetition> result = service.listForTeam(12L, null, null, 5);
        assertEquals(List.of(newer, older), result.stream().map(CompetitionService.TeamCompetition::competition).toList());
        assertEquals(progress, result.get(1).progress());
        assertEquals(List.of(newer), service.listForTeam(12L, null, null, 1).stream()
                .map(CompetitionService.TeamCompetition::competition).toList());
    }

    @Test void dependencyFailureDoesNotCreateCompetition() {
        when(institutions.getSport(1L)).thenThrow(new UpstreamServiceException("offline"));
        assertThrows(UpstreamServiceException.class, () -> service.create(cup()));
        verify(repository, never()).save(any());
        verifyNoInteractions(matchService);
    }

    @Test void creatingCupCreatesItsBracket() {
        when(institutions.getSport(1L)).thenReturn(new SportInfo(1L, "Fútbol"));
        when(repository.save(any(Competition.class))).thenAnswer(call -> {
            Competition saved = call.getArgument(0);
            saved.setId(4L);
            return saved;
        });
        service.create(cup());
        verify(matchService).createBracket(4L);
    }

    @Test void onlyCupsCanBeCreated() {
        Competition league = cup();
        league.setType(CompetitionType.LEAGUE);
        assertThrows(ConflictException.class, () -> service.create(league));
        verify(repository, never()).save(any());
    }

    @Test void sportCannotChangeWithEnrolledTeams() {
        Competition competition = existing();
        when(enrollments.existsByCompetitionId(4L)).thenReturn(true);
        Competition changes = new Competition();
        changes.setSportId(2L);
        assertThrows(ConflictException.class, () -> service.update(4L, changes));
        assertEquals(1L, competition.getSportId());
        verifyNoInteractions(institutions);
    }

    @Test void typeCannotChange() {
        Competition competition = existing();
        competition.setType(CompetitionType.CUP);
        Competition changes = cup();
        changes.setType(CompetitionType.LEAGUE);
        assertThrows(ConflictException.class, () -> service.update(4L, changes));
        verify(repository, never()).save(any());
    }

    @Test void startedCupCanBeUpdated() {
        Competition competition = existing();
        competition.setType(CompetitionType.CUP);
        competition.setStatus(CompetitionStatus.IN_PROGRESS);
        when(repository.save(any(Competition.class))).then(returnsFirstArg());
        Competition changes = cup();
        changes.setCompetitionImg("https://ejemplo.com/copa.png");
        service.update(4L, changes);
        assertEquals("https://ejemplo.com/copa.png", competition.getCompetitionImg());
    }

    @Test void onlyForwardStatusTransitionsAreAccepted() {
        Competition competition = existing();
        assertThrows(ConflictException.class, () -> service.changeStatus(4L, CompetitionStatus.FINISHED));
        service.changeStatus(4L, CompetitionStatus.IN_PROGRESS);
        assertEquals(CompetitionStatus.IN_PROGRESS, competition.getStatus());
        assertThrows(ConflictException.class, () -> service.changeStatus(4L, CompetitionStatus.DRAFT));
        service.changeStatus(4L, CompetitionStatus.FINISHED);
        assertEquals(CompetitionStatus.FINISHED, competition.getStatus());
    }

    @Test void cupStartsWithSixteenTeamsAndCompleteDraw() {
        Competition competition = existing();
        competition.setType(CompetitionType.CUP);
        when(repository.save(any(Competition.class))).then(returnsFirstArg());

        when(enrollments.countByCompetitionId(4L)).thenReturn(15L);
        assertThrows(ConflictException.class, () -> service.changeStatus(4L, CompetitionStatus.IN_PROGRESS));
        when(enrollments.countByCompetitionId(4L)).thenReturn(16L);
        assertThrows(ConflictException.class, () -> service.changeStatus(4L, CompetitionStatus.IN_PROGRESS));
        assertEquals(CompetitionStatus.DRAFT, competition.getStatus());

        when(matchService.isDrawComplete(4L)).thenReturn(true);
        service.changeStatus(4L, CompetitionStatus.IN_PROGRESS);
        assertEquals(CompetitionStatus.IN_PROGRESS, competition.getStatus());

        // La copa termina sola con la final
        assertThrows(ConflictException.class, () -> service.changeStatus(4L, CompetitionStatus.FINISHED));
    }

    @Test void activeCompetitionCannotBeDeleted() {
        Competition competition = existing();
        competition.setStatus(CompetitionStatus.IN_PROGRESS);
        assertThrows(ConflictException.class, () -> service.delete(4L));
        verify(repository, never()).delete(any());
        verifyNoInteractions(matchService);
    }

    @Test void deletingDraftAlsoDeletesItsBracket() {
        Competition competition = existing();
        service.delete(4L);
        verify(matchService).deleteByCompetition(4L);
        verify(repository).delete(competition);
    }
}
