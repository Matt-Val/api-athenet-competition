package com.athenet.competition.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.athenet.competition.config.SecurityConfig;
import com.athenet.competition.mapper.*;
import com.athenet.competition.model.*;
import com.athenet.competition.model.enums.*;
import com.athenet.competition.service.*;

@WebMvcTest(controllers = {PublicCompetitionController.class, PublicMatchController.class})
@Import({SecurityConfig.class, CompetitionMapper.class, EnrollmentMapper.class, MatchMapper.class})
class PublicTeamEndpointsTest {
    @Autowired MockMvc mvc;
    @MockitoBean CompetitionService competitions;
    @MockitoBean EnrollmentService enrollments;
    @MockitoBean MatchService matches;
    @MockitoBean JwtDecoder decoder;

    Competition cup() {
        Competition cup = new Competition();
        cup.setId(3L);
        cup.setName("Copa Invierno");
        cup.setSeason("2026");
        cup.setStatus(CompetitionStatus.FINISHED);
        return cup;
    }

    @Test void teamCompetitionsKeepCompetitionFieldsAndAddProgress() throws Exception {
        TeamProgress progress = new TeamProgress(MatchRound.SEMI_FINAL, TeamOutcome.ELIMINATED, 3, 2, 1);
        when(competitions.listForTeam(5L, null, null, 5))
                .thenReturn(List.of(new CompetitionService.TeamCompetition(cup(), progress)));

        mvc.perform(get("/api/public/competitions").param("teamId", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].name").value("Copa Invierno"))
                .andExpect(jsonPath("$[0].status").value("FINISHED"))
                .andExpect(jsonPath("$[0].competition").doesNotExist())
                .andExpect(jsonPath("$[0].teamProgress.round").value("SEMI_FINAL"))
                .andExpect(jsonPath("$[0].teamProgress.outcome").value("ELIMINATED"))
                .andExpect(jsonPath("$[0].teamProgress.played").value(3))
                .andExpect(jsonPath("$[0].teamProgress.lost").value(1));
    }

    @Test void teamMatchesShowBothTeamsWithoutDelegate() throws Exception {
        Match match = new Match();
        match.setId(38L);
        match.setCompetitionId(3L);
        match.setRound(MatchRound.SEMI_FINAL);
        match.setDate(LocalDate.of(2026, 7, 12));
        match.setTime(LocalTime.of(16, 30));
        match.setStatus(MatchStatus.FINISHED);
        match.setHomeScore(1);
        match.setAwayScore(2);
        match.setDelegateOid("3f2b9c1e-5a7d-4c1b-9e2f-1d0a8b6c4e21");
        Enrollment home = new Enrollment(1L, 3L, 5L, 1L, "Halcones FC", "https://img/halcones.png");
        Enrollment away = new Enrollment(2L, 3L, 9L, 2L, "Toros UC", null);
        when(matches.listForTeam(5L, MatchStatus.FINISHED, 5))
                .thenReturn(List.of(new MatchService.TeamMatch(match, cup(), home, away)));

        mvc.perform(get("/api/public/matches").param("teamId", "5").param("status", "FINISHED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(38))
                .andExpect(jsonPath("$[0].competition.name").value("Copa Invierno"))
                .andExpect(jsonPath("$[0].competition.season").value("2026"))
                .andExpect(jsonPath("$[0].time").value("16:30"))
                .andExpect(jsonPath("$[0].home.teamId").value(5))
                .andExpect(jsonPath("$[0].home.name").value("Halcones FC"))
                .andExpect(jsonPath("$[0].home.score").value(1))
                .andExpect(jsonPath("$[0].away.name").value("Toros UC"))
                .andExpect(jsonPath("$[0].away.score").value(2))
                .andExpect(jsonPath("$[0].delegateOid").doesNotExist())
                .andExpect(jsonPath("$[0].version").doesNotExist());
    }

    @Test void teamMatchesRequireTeamId() throws Exception {
        mvc.perform(get("/api/public/matches")).andExpect(status().isBadRequest());
        verifyNoInteractions(matches);
    }

    @Test void limitGoesFromOneToTwenty() throws Exception {
        mvc.perform(get("/api/public/matches").param("teamId", "5").param("limit", "21"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/public/competitions").param("teamId", "5").param("limit", "0"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(matches, competitions);
    }

    @Test void competitionsWithoutTeamIdKeepTheirListing() throws Exception {
        when(competitions.listForPublic(1L, null)).thenReturn(List.of(cup()));
        mvc.perform(get("/api/public/competitions").param("sportId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].teamProgress").doesNotExist());
        verify(competitions, never()).listForTeam(any(), any(), any(), anyInt());
    }
}
