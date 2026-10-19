package com.athenet.competition.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/* 
    Un equipo inscrito es una competencia. CompetitionId y TeamId Son IDS planas.
    Competition es local, pero Team es de MS-Instituciones, así que todo el registro se trata igual.
*/

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "enrollment", uniqueConstraints = @UniqueConstraint(columnNames = {"competition_id", "team_id"}))
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "competition_id", nullable = false)
    private Long competitionId;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "institution_id", nullable = false)
    private Long institutionId;

    // Copia de MS-Instituciones: evita depender de ese servicio en
    // cada lectura del fixture o la tabla de posiciones.
    @Column(name = "team_name", nullable = false, length = 150)
    private String teamName;

    @Column(name = "team_logo_url", length = 500)
    private String teamLogoUrl;
}
