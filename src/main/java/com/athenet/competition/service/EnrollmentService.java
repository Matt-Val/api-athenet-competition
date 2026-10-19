package com.athenet.competition.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.athenet.competition.client.InstitutionClient;
import com.athenet.competition.client.TeamInfo;
import com.athenet.competition.exception.ConflictException;
import com.athenet.competition.exception.ResourceNotFoundException;
import com.athenet.competition.model.Competition;
import com.athenet.competition.model.Enrollment;
import com.athenet.competition.model.enums.CompetitionType;
import com.athenet.competition.repository.CompetitionRepository;
import com.athenet.competition.repository.EnrollmentRepository;
import com.athenet.competition.repository.MatchRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final CompetitionRepository competitionRepository;
    private final MatchRepository matchRepository;
    private final InstitutionClient institutionClient;

    /* 
        Inscribe un equipo en una competencia. Le pregunta a MS-Instituciones por
        el equipo (existe, está activo, de qué deporte es, y los datos de su institución)
        en vez de confiar en lo que mande el cliente - así nadie puede inscribir un equipo
        inventado o de otro deporte solo armando el JSON a mano.
    */
    @Transactional
    public Enrollment enroll(Long competitionId, Long teamId) {
        Competition competition = competitionRepository.findLockedById(competitionId)
                .orElseThrow(() -> new ResourceNotFoundException("Competencia con id " + competitionId + " no fue encontrada"));

        if (enrollmentRepository.existsByCompetitionIdAndTeamId(competitionId, teamId)) {
            throw new ConflictException("El equipo " + teamId + " ya está inscrito en esta competencia");
        }

        if (competition.getType() == CompetitionType.CUP
                && enrollmentRepository.countByCompetitionId(competitionId) >= Competition.CUP_TEAMS) {
            throw new ConflictException("La copa ya tiene sus " + Competition.CUP_TEAMS + " equipos inscritos");
        }

        TeamInfo team = institutionClient.getActiveTeam(teamId);

        if (!team.sportId().equals(competition.getSportId())) {
            throw new ConflictException(
                    "El equipo " + teamId + " no es del mismo deporte que la competencia");
        }

        Enrollment enrollment = new Enrollment();
        enrollment.setCompetitionId(competitionId);
        enrollment.setTeamId(teamId);
        enrollment.setInstitutionId(team.institutionId());
        enrollment.setTeamName(team.name());
        enrollment.setTeamLogoUrl(team.logoUrl());

        return enrollmentRepository.save(enrollment);
    }


    /* 
        Desinscribe un equipo. Solo si no tiene partidos registrados en esa competencia.
        Si ya tiene fixturem, sacarlo rompería el historial.
    */
    @Transactional
    public void unenroll(Long competitionId, Long teamId) {
        competitionRepository.findLockedById(competitionId)
                .orElseThrow(() -> new ResourceNotFoundException("Competencia no encontrada"));
        Enrollment enrollment = enrollmentRepository.findByCompetitionIdAndTeamId(competitionId, teamId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "El equipo " + teamId + " no está inscrito en la competencia " + competitionId));

        if (matchRepository.existsByCompetitionIdAndTeamId(competitionId, teamId)) {
            throw new ConflictException(
                    "No se puede quitar al equipo " + teamId + ": ya tiene partidos en esta competencia");
        }

        enrollmentRepository.delete(enrollment);
    }

    public List<Enrollment> listByCompetition(Long competitionId) {
        return enrollmentRepository.findByCompetitionId(competitionId);
    }
}
