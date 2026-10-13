package com.athenet.competition.model;

import java.time.LocalDate;
import java.time.LocalTime;

import com.athenet.competition.model.enums.MatchStatus;

import jakarta.persistence.*;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/* 
    Un partido es un Event con type=MATCH en MS-Eventos. Este registro es la copia que usa 
    Competencia para fixture y resultado. 
    EventId es único: al consumir evento.creado/evento.actualizado se hace upsert por eventId
*/
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "match")
public class Match {

    // Id de la tabla, no confundir con eventId
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "competition_id", nullable = false)
    private Long competitionId;

    @Column(name = "event_id", nullable = false, unique = true)
    private Long eventId;

    @Column(name = "event_internal_id", nullable = false, unique = true, length = 50)
    private String eventInternalId;

    @Column(nullable = false)
    private LocalDate date;

    // Opcional, igual que startTime en Eventos: hora en America/Santiago.
    private LocalTime time;
 
    @Column(name = "home_team_id", nullable = false)
    private Long homeTeamId;

    @Column(name = "away_team_id", nullable = false)
    private Long awayTeamId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchStatus status = MatchStatus.SCHEDULED;

    @Column(name = "home_score")
    private Integer homeScore;

    @Column(name = "away_score")
    private Integer awayScore;

    @Column(nullable = false)
    private boolean simulated = false;

    // Idempotencia para MS-Ranking: solo procesa el mensaje si version es
    // mayor que la guardada. Sube en cada corrección de resultado.
    @Column(nullable = false)
    private int version = 0;
}
