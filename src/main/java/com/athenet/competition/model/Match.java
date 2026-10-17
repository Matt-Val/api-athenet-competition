package com.athenet.competition.model;

import java.time.LocalDate;
import java.time.LocalTime;

import com.athenet.competition.model.enums.MatchRound;
import com.athenet.competition.model.enums.MatchStatus;

import jakarta.persistence.*;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/*
    Encuentro de la llave de una copa. Competencia es su dueña: los partidos se crean
    vacíos junto con la copa, el director los programa y el delegado asignado carga el
    resultado.
*/
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "match", uniqueConstraints = @UniqueConstraint(columnNames = {"competition_id", "round", "position"}))
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "competition_id", nullable = false)
    private Long competitionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchRound round;

    // 1..N dentro de la ronda
    @Column(nullable = false)
    private int position;

    private LocalDate date;

    // Hora en America/Santiago, igual que startTime en Eventos.
    private LocalTime time;

    private String location;

    private String address;

    // Null hasta el sorteo (octavos) o hasta que gane el partido anterior
    @Column(name = "home_team_id")
    private Long homeTeamId;

    @Column(name = "away_team_id")
    private Long awayTeamId;

    // Delegado (Entra ID): el único que puede iniciar el partido y cargar o corregir su resultado
    @Column(name = "delegate_oid", length = 36)
    private String delegateOid;

    @Column(name = "delegate_display_name", length = 256)
    private String delegateDisplayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchStatus status = MatchStatus.SCHEDULED;

    @Column(name = "home_score")
    private Integer homeScore;

    @Column(name = "away_score")
    private Integer awayScore;

    // Idempotencia para el consumidor de resultado.registrado: solo procesa el mensaje si version es
    // mayor que la guardada. Sube en cada corrección de resultado.
    @Column(nullable = false)
    private int version = 0;

    // Completo: se puede jugar
    public boolean isComplete() {
        return homeTeamId != null && awayTeamId != null && date != null && time != null && delegateOid != null;
    }
}
