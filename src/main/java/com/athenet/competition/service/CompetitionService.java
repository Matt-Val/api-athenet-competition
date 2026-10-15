package com.athenet.competition.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.athenet.competition.client.InstitutionClient;
import com.athenet.competition.exception.ConflictException;
import com.athenet.competition.exception.ResourceNotFoundException;
import com.athenet.competition.model.Competition;
import com.athenet.competition.model.enums.CompetitionStatus;
import com.athenet.competition.model.enums.CompetitionType;
import com.athenet.competition.repository.CompetitionRepository;
import com.athenet.competition.repository.EnrollmentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompetitionService {

    private final CompetitionRepository competitionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final InstitutionClient institutionClient;
    private final MatchService matchService;

    /**
        Crea una nueva competencia. Nace siempre en estado DRAFT (el campo
        tiene ese default en la entidad). Valida contra MS-Instituciones que
        sportId exista antes de guardar — si no existe o el servicio
        no responde, institutionClient.getSport() ya tira la excepción
        correspondiente (404/503) y acá no llega a guardarse nada.
        Por ahora solo hay copas: junto con la copa se crea su llave vacía.
     */
    @Transactional
    public Competition create(Competition competition) {
        if (competition.getType() != CompetitionType.CUP) {
            throw new ConflictException("Por ahora solo se pueden crear copas");
        }
        institutionClient.getSport(competition.getSportId());
        Competition saved = competitionRepository.save(competition);
        matchService.createBracket(saved.getId());
        return saved;
    }

    public Competition getById(Long id) {
        return findEntityById(id);
    }


    // GET /api/admin/competencias?deporteId — incluye borradores.
    public List<Competition> listForAdmin(Long sportId) {
        return sportId != null
                ? competitionRepository.findBySportId(sportId)
                : competitionRepository.findAll();
    }

    

    // GET /api/public/competencias?deporteId&estado — nunca incluye borradores.
    public List<Competition> listForPublic(Long sportId, CompetitionStatus status) {
        return competitionRepository.findPublic(sportId, status);
    }

    /*
        Actualiza una competencia existente. No se puede cambiar el tipo, ni sportId si ya
        hay equipos inscritos. Si ya se inició, sus partidos se republican en Eventos
        (el nombre y la imagen van en la copia).
     */
    @Transactional
    public Competition update(Long id, Competition changes) {
        Competition competition = findEntityById(id);

        if (competition.getType() != changes.getType()) {
            throw new ConflictException("No se puede cambiar el tipo de una competencia");
        }

        boolean sportChanged = !competition.getSportId().equals(changes.getSportId());
        if (sportChanged) {
            if (enrollmentRepository.existsByCompetitionId(id)) {
                throw new ConflictException(
                        "No se puede cambiar el deporte de una competencia que ya tiene equipos inscritos");
            }
            institutionClient.getSport(changes.getSportId());
        }

        competition.setName(changes.getName());
        competition.setType(changes.getType());
        competition.setSportId(changes.getSportId());
        competition.setSeason(changes.getSeason());
        competition.setDescription(changes.getDescription());
        competition.setCompetitionImg(changes.getCompetitionImg());
        competition.setStartDate(changes.getStartDate());
        competition.setEndDate(changes.getEndDate());
        competition.setWinPoints(changes.getWinPoints());
        competition.setDrawPoints(changes.getDrawPoints());
        competition.setLossPoints(changes.getLossPoints());

        Competition saved = competitionRepository.save(competition);
        if (saved.getStatus() != CompetitionStatus.DRAFT) {
            matchService.publishSchedule(saved);
        }
        return saved;
    }

    /*
        PATCH .../estado — solo permite avanzar: DRAFT -> IN_PROGRESS -> FINISHED.
        Una copa se inicia con sus 16 equipos y los octavos sorteados, y termina sola
        con el resultado de la final. Al iniciarla, sus partidos completos se publican en Eventos.
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

        if (competition.getType() == CompetitionType.CUP) {
            if (newStatus == CompetitionStatus.FINISHED) {
                throw new ConflictException("La copa finaliza al registrar el resultado de la final");
            }
            if (enrollmentRepository.countByCompetitionId(id) != Competition.CUP_TEAMS || !matchService.isDrawComplete(id)) {
                throw new ConflictException("Para iniciar la copa se necesitan " + Competition.CUP_TEAMS
                        + " equipos inscritos y los octavos sorteados");
            }
        }

        competition.setStatus(newStatus);
        Competition saved = competitionRepository.save(competition);
        matchService.publishSchedule(saved);
        return saved;
    }

    /*
        DELETE .../{id} — solo si está en DRAFT. Se borra también su llave
        (nunca llegó a Eventos: los borradores no se publican).
     */
    @Transactional
    public void delete(Long id) {
        Competition competition = findEntityById(id);

        if (competition.getStatus() != CompetitionStatus.DRAFT) {
            throw new ConflictException("Solo se puede eliminar una competencia en estado DRAFT");
        }

        matchService.deleteByCompetition(id);
        competitionRepository.delete(competition);
    }

    private Competition findEntityById(Long id) {
        return competitionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Competencia con id " + id + " no fue encontrada"));
    }
}
