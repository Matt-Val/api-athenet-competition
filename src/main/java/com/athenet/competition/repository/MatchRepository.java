package com.athenet.competition.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.athenet.competition.model.Match;
import com.athenet.competition.model.enums.MatchRound;

public interface MatchRepository extends JpaRepository<Match, Long> {

    // Fixture completo de una competencia
    List<Match> findByCompetitionId(Long competitionId);

    List<Match> findByCompetitionIdAndRound(Long competitionId, MatchRound round);

    // Partido siguiente de la llave, al que avanza el ganador
    Optional<Match> findByCompetitionIdAndRoundAndPosition(Long competitionId, MatchRound round, int position);

    // "Mis eventos" del delegado
    List<Match> findByDelegateOid(String delegateOid);

    void deleteByCompetitionId(Long competitionId);

    // Usado al desinscribir un equipo: DELETE .../equipos/{equipoId} solo se
    // permite si el equipo no tiene partidos en esa competencia.
    @Query("""
            SELECT COUNT(m) > 0 FROM Match m
            WHERE m.competitionId = :competitionId
              AND (m.homeTeamId = :teamId OR m.awayTeamId = :teamId)
            """)
    boolean existsByCompetitionIdAndTeamId(@Param("competitionId") Long competitionId, @Param("teamId") Long teamId);

    // Perfil del equipo: sus partidos en todas las competencias
    @Query("""
            SELECT m FROM Match m
            WHERE m.homeTeamId = :teamId OR m.awayTeamId = :teamId
            """)
    List<Match> findByTeamId(@Param("teamId") Long teamId);
}
