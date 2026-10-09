package com.athenet.competition.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.athenet.competition.exception.ConflictException;
import com.athenet.competition.exception.ResourceNotFoundException;
import com.athenet.competition.model.Competition;
import com.athenet.competition.model.enums.CompetitionStatus;
import com.athenet.competition.repository.CompetitionRepository;
import com.athenet.competition.repository.EnrollmentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompetitionService {

    private final CompetitionRepository competitionRepository;
    private final EnrollmentRepository enrollmentRepository;

    /**
        Crea una nueva competencia. Nace siempre en estado DRAFT (el campo
        tiene ese default en la entidad).
        Nota: falta validar que sportId exista en MS-Instituciones
        (GET /api/v1/deportes/{id}) — se agrega cuando construyamos el
        cliente REST hacia ese servicio.
     */
    @Transactional
    public Competition create(Competition competition) {
        return competitionRepository.save(competition);
    }

    public Competition getById(Long id) {
        return findEntityById(id);
    }

    /*
        GET /api/admin/competencias?deporteId — incluye borradores.
     */
    public List<Competition> listForAdmin(Long sportId) {
        return sportId != null
                ? competitionRepository.findBySportId(sportId)
                : competitionRepository.findAll();
    }

    /*
        GET /api/public/competencias?deporteId&estado — nunca incluye borradores.
     */
    public List<Competition> listForPublic(Long sportId, CompetitionStatus status) {
        return competitionRepository.findPublic(sportId, status);
    }

    /*
        Actualiza una competencia existente. No se puede cambiar sportId si ya
        hay equipos inscritos (regla del §6.3).
     */
    @Transactional
    public Competition update(Long id, Competition changes) {
        Competition competition = findEntityById(id);

        if (!competition.getSportId().equals(changes.getSportId())
                && enrollmentRepository.existsByCompetitionId(id)) {
            throw new ConflictException(
                    "No se puede cambiar el deporte de una competencia que ya tiene equipos inscritos");
        }

        competition.setName(changes.getName());
        competition.setType(changes.getType());
        competition.setSportId(changes.getSportId());
        competition.setSeason(changes.getSeason());
        competition.setDescription(changes.getDescription());
        competition.setStartDate(changes.getStartDate());
        competition.setEndDate(changes.getEndDate());
        competition.setWinPoints(changes.getWinPoints());
        competition.setDrawPoints(changes.getDrawPoints());
        competition.setLossPoints(changes.getLossPoints());

        return competitionRepository.save(competition);
    }

    /*
        PATCH .../estado — solo permite avanzar: DRAFT -> IN_PROGRESS -> FINISHED.
     */
    @Transactional
    public Competition changeStatus(Long id, CompetitionStatus newStatus) {
        Competition competition = findEntityById(id);
        CompetitionStatus current = competition.getStatus();

        boolean validTransition = (current == CompetitionStatus.DRAFT && newStatus == CompetitionStatus.IN_PROGRESS)
                || (current == CompetitionStatus.IN_PROGRESS && newStatus == CompetitionStatus.FINISHED);

        if (!validTransition) {
            throw new ConflictException(
                    "No se puede pasar de " + current + " a " + newStatus);
        }

        competition.setStatus(newStatus);
        return competitionRepository.save(competition);
    }

    /*
        DELETE .../{id} — solo si está en DRAFT.
     */
    @Transactional
    public void delete(Long id) {
        Competition competition = findEntityById(id);

        if (competition.getStatus() != CompetitionStatus.DRAFT) {
            throw new ConflictException("Solo se puede eliminar una competencia en estado DRAFT");
        }

        competitionRepository.delete(competition);
    }

    private Competition findEntityById(Long id) {
        return competitionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Competencia con id " + id + " no fue encontrada"));
    }
}
