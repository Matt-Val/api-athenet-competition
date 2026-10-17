package com.athenet.competition.model;

import java.time.LocalDate;

import com.athenet.competition.model.enums.CompetitionStatus;
import com.athenet.competition.model.enums.CompetitionType;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "competition")
public class Competition {

    // Equipos de una copa: la llave parte en octavos
    public static final int CUP_TEAMS = 16;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CompetitionType type;

    // Dueño del catálogo: MS-Instituciones. Solo guardamos el ID
    @Column(name = "sport_id", nullable = false)
    private Long sportId;

    @Column(nullable = false, length = 20)
    private String season;

    @Column(columnDefinition = "TEXT")
    private String description;

    // URI de la imagen
    @Column(name = "competition_img", length = 500)
    private String competitionImg;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CompetitionStatus status = CompetitionStatus.DRAFT;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "win_points", nullable = false)
    private int winPoints = 3;

    @Column(name = "draw_points", nullable = false)
    private int drawPoints = 1;

    @Column(name = "loss_points", nullable = false)
    private int lossPoints = 0;
}
