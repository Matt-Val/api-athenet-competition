package com.athenet.competition.repository;

import java.util.Collection;
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

    // Perfil del equipo: competencias en las que está inscrito
    List<Enrollment> findByTeamId(Long teamId);

    // Nombres y logos de los equipos en los partidos del perfil
    List<Enrollment> findByCompetitionIdIn(Collection<Long> competitionIds);
}
