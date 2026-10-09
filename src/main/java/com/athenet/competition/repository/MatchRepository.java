package com.athenet.competition.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.athenet.competition.model.Match;

public interface MatchRepository extends JpaRepository<Match, Long> {

    // Fixture completo de una competencia
    List<Match> findByCompetitionId(Long competitionId);

    // Clave para el upsert por eventId al consumir evento.creado/evento.actualizado
    // y para GET /api/public/partidos?eventId=(marcador en el detalle del evento).
    Optional<Match> findByEventId(Long eventId);

    // Usado al desinscribir un equipo: DELETE .../equipos/{equipoId} solo se
    // permite si el equipo no tiene partidos en esa competencia.
    @Query("""
            SELECT COUNT(m) > 0 FROM Match m
            WHERE m.competitionId = :competitionId
              AND (m.homeTeamId = :teamId OR m.awayTeamId = :teamId)
            """)
    boolean existsByCompetitionIdAndTeamId(@Param("competitionId") Long competitionId, @Param("teamId") Long teamId);
}
