package com.athenet.competition.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.athenet.competition.client.InstitutionClient;
import com.athenet.competition.exception.ConflictException;
import com.athenet.competition.exception.UpstreamServiceException;
import com.athenet.competition.model.Competition;
import com.athenet.competition.model.enums.CompetitionStatus;
import com.athenet.competition.repository.*;

class CompetitionServiceTest {
    final CompetitionRepository repository = mock(CompetitionRepository.class);
    final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
    final InstitutionClient institutions = mock(InstitutionClient.class);
    final CompetitionService service = new CompetitionService(repository, enrollments, institutions);

    Competition existing() {
        Competition competition = new Competition();
        competition.setSportId(1L);
        when(repository.findById(4L)).thenReturn(Optional.of(competition));
        return competition;
    }

    @Test void dependencyFailureDoesNotCreateCompetition() {
        Competition competition = new Competition();
        competition.setSportId(1L);
        when(institutions.getSport(1L)).thenThrow(new UpstreamServiceException("offline"));
        assertThrows(UpstreamServiceException.class, () -> service.create(competition));
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

    @Test void onlyForwardStatusTransitionsAreAccepted() {
        Competition competition = existing();
        assertThrows(ConflictException.class, () -> service.changeStatus(4L, CompetitionStatus.FINISHED));
        service.changeStatus(4L, CompetitionStatus.IN_PROGRESS);
        assertEquals(CompetitionStatus.IN_PROGRESS, competition.getStatus());
        assertThrows(ConflictException.class, () -> service.changeStatus(4L, CompetitionStatus.DRAFT));
        service.changeStatus(4L, CompetitionStatus.FINISHED);
        assertEquals(CompetitionStatus.FINISHED, competition.getStatus());
    }

    @Test void activeCompetitionCannotBeDeleted() {
        Competition competition = existing();
        competition.setStatus(CompetitionStatus.IN_PROGRESS);
        assertThrows(ConflictException.class, () -> service.delete(4L));
        verify(repository, never()).delete(any());
    }
}
