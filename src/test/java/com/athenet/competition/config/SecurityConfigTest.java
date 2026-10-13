package com.athenet.competition.config;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.athenet.competition.controller.*;
import com.athenet.competition.mapper.*;
import com.athenet.competition.model.Match;
import com.athenet.competition.service.*;

@WebMvcTest(controllers = {AdminCompetitionController.class, PublicCompetitionController.class, PublicMatchController.class})
@Import({SecurityConfig.class, CompetitionMapper.class, EnrollmentMapper.class, MatchMapper.class})
class SecurityConfigTest {
    @Autowired MockMvc mvc;
    @MockitoBean CompetitionService competitions;
    @MockitoBean EnrollmentService enrollments;
    @MockitoBean MatchService matches;
    @MockitoBean JwtDecoder decoder;

    void token(String role) {
        when(decoder.decode("test-token")).thenReturn(Jwt.withTokenValue("test-token")
                .header("alg", "RS256").subject("test-user")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
                .claim("roles", List.of(role)).build());
    }

    @Test void publicReadsDoNotNeedToken() throws Exception {
        when(competitions.listForPublic(null, null)).thenReturn(List.of());
        mvc.perform(get("/api/public/competitions")).andExpect(status().isOk());
    }

    @Test void adminRequiresToken() throws Exception {
        mvc.perform(post("/api/admin/competitions/1/matches/1/simulate"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(matches);
    }

    @Test void adminRoleCannotSimulate() throws Exception {
        token("admin");
        mvc.perform(post("/api/admin/competitions/1/matches/1/simulate")
                .header("Authorization", "Bearer test-token")).andExpect(status().isForbidden());
        verifyNoInteractions(matches);
    }

    @Test void directorRoleIsMappedFromActualJwtClaims() throws Exception {
        token("director");
        when(matches.simulate(1L, 2L)).thenReturn(new Match());
        mvc.perform(post("/api/admin/competitions/1/matches/2/simulate")
                .header("Authorization", "Bearer test-token")).andExpect(status().isOk());
        verify(matches).simulate(1L, 2L);
    }

    @Test void invalidScoreReturns400BeforeService() throws Exception {
        token("director");
        mvc.perform(put("/api/admin/competitions/1/matches/2/result")
                .header("Authorization", "Bearer test-token").contentType(MediaType.APPLICATION_JSON)
                .content("{\"homeScore\":-1,\"awayScore\":0}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(matches);
    }

    @Test void malformedJsonReturns400() throws Exception {
        token("director");
        mvc.perform(put("/api/admin/competitions/1/matches/2/result")
                .header("Authorization", "Bearer test-token").contentType(MediaType.APPLICATION_JSON)
                .content("{"))
                .andExpect(status().isBadRequest());
    }
}
