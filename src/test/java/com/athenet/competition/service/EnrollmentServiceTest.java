package com.athenet.competition.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.athenet.competition.client.*;
import com.athenet.competition.exception.ConflictException;
import com.athenet.competition.model.*;
import com.athenet.competition.model.enums.CompetitionType;
import com.athenet.competition.repository.*;

class EnrollmentServiceTest {
    final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
    final CompetitionRepository competitions = mock(CompetitionRepository.class);
    final MatchRepository matches = mock(MatchRepository.class);
    final InstitutionClient institutions = mock(InstitutionClient.class);
    final EnrollmentService service = new EnrollmentService(enrollments, competitions, matches, institutions);

    void competition() {
        Competition competition = new Competition();
        competition.setSportId(1L);
        when(competitions.findLockedById(4L)).thenReturn(Optional.of(competition));
    }

    @Test void enrollmentCopiesTeamAndInstitutionData() {
        competition();
        when(institutions.getActiveTeam(12L)).thenReturn(new TeamInfo(12L, "Equipo", "logo", 1L, 3L, "Institución"));
        service.enroll(4L, 12L);
        var captor = ArgumentCaptor.forClass(Enrollment.class);
        verify(enrollments).save(captor.capture());
        assertEquals(3L, captor.getValue().getInstitutionId());
        assertEquals("Equipo", captor.getValue().getTeamName());
        assertEquals("logo", captor.getValue().getTeamLogoUrl());
    }

    @Test void duplicateEnrollmentIsRejectedBeforeRemoteCall() {
        competition();
        when(enrollments.existsByCompetitionIdAndTeamId(4L, 12L)).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.enroll(4L, 12L));
        verifyNoInteractions(institutions);
    }

    @Test void differentSportIsRejected() {
        competition();
        when(institutions.getActiveTeam(12L)).thenReturn(new TeamInfo(12L, "Equipo", null, 2L, 3L, "Institución"));
        assertThrows(ConflictException.class, () -> service.enroll(4L, 12L));
        verify(enrollments, never()).save(any());
    }

    @Test void cupAcceptsSixteenTeamsAtMost() {
        Competition cup = new Competition();
        cup.setSportId(1L);
        cup.setType(CompetitionType.CUP);
        when(competitions.findLockedById(4L)).thenReturn(Optional.of(cup));
        when(enrollments.countByCompetitionId(4L)).thenReturn(16L);
        assertThrows(ConflictException.class, () -> service.enroll(4L, 12L));
        verifyNoInteractions(institutions);
        verify(enrollments, never()).save(any());
    }

    @Test void teamWithMatchesCannotBeRemoved() {
        competition();
        when(enrollments.findByCompetitionIdAndTeamId(4L, 12L)).thenReturn(Optional.of(new Enrollment()));
        when(matches.existsByCompetitionIdAndTeamId(4L, 12L)).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.unenroll(4L, 12L));
        verify(enrollments, never()).delete(any());
    }
}
