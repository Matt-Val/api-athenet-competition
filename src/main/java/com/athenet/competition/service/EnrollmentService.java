package com.athenet.competition.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.athenet.competition.exception.ConflictException;
import com.athenet.competition.exception.ResourceNotFoundException;
import com.athenet.competition.model.Enrollment;
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

    /* 
        Inscribe un equipo en una competencia.
        Nota: teamName/teamLogoUrl/institutionId deben venir ya resueltos por
        quien llama (el controller, más adelante) consultando 
        GET /api/v1/equipos/{id} en MS-Instituciones - ahí es donde se valida que
        el equipo exista, esté activo y que sea del mismo deporte.
        Esta capa solo persiste la copia y protege duplicados.
    */
    @Transactional
    public Enrollment enroll(Long competitionId, Long teamId, Long institutionId,
            String teamName, String teamLogoUrl) {
        if (!competitionRepository.existsById(competitionId)) {
            throw new ResourceNotFoundException("Competencia con id " + competitionId + " no fue encontrada");
        }

        if (enrollmentRepository.existsByCompetitionIdAndTeamId(competitionId, teamId)) {
            throw new ConflictException("El equipo " + teamId + " ya está inscrito en esta competencia");
        }

        Enrollment enrollment = new Enrollment();
        enrollment.setCompetitionId(competitionId);
        enrollment.setTeamId(teamId);
        enrollment.setInstitutionId(institutionId);
        enrollment.setTeamName(teamName);
        enrollment.setTeamLogoUrl(teamLogoUrl);

        return enrollmentRepository.save(enrollment);
    }

    /* 
        Desinscribe un equipo. SOlo si no tiene partidos registrados en esa competencia.
        Si ya tiene fixture, sacarlo rompería el historial.
    */
    @Transactional
    public void unenroll(Long competitionId, Long teamId) {
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
