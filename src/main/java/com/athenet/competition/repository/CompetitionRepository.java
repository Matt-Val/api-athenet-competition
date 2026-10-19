package com.athenet.competition.repository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.athenet.competition.model.Competition;
import com.athenet.competition.model.enums.CompetitionStatus;

public interface CompetitionRepository extends JpaRepository<Competition, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Competition c WHERE c.id = :id")
    Optional<Competition> findLockedById(@Param("id") Long id);

    // GET /api/admin/competencias?deporteId — incluye borradores
    List<Competition> findBySportId(Long sportId);

    

    /* 
      GET /api/public/competencias?deporteId&estado — sportId y status son
      opcionales (0, 1 o ambos filtros), y SIEMPRE excluye BORRADOR: 
      el público no debe ver competencias que el director todavía no publicó.
    */
    @Query("""
            SELECT c FROM Competition c
            WHERE c.status <> com.athenet.competition.model.enums.CompetitionStatus.DRAFT
              AND (:sportId IS NULL OR c.sportId = :sportId)
              AND (:status IS NULL OR c.status = :status)
            """)
    List<Competition> findPublic(@Param("sportId") Long sportId, @Param("status") CompetitionStatus status);
}
