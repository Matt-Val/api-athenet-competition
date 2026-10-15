package com.athenet.competition.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.athenet.competition.model.Enrollment;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    // Nómina de una competencia (pestaña "Equipos" / tabla de posiciones)
    List<Enrollment> findByCompetitionId(Long competitionId);

    Optional<Enrollment> findByCompetitionIdAndTeamId(Long competitionId, Long teamId);

    // Evita inscribir dos veces al mismo equipo en la misma competencia
    boolean existsByCompetitionIdAndTeamId(Long competitionId, Long teamId);

    // Usado al editar una competencia: no deja cambiar el deporte si ya hay equipos inscritos
    boolean existsByCompetitionId(Long competitionId);

    // Cupo de la copa
    long countByCompetitionId(Long competitionId);
}
