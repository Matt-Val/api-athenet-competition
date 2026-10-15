package com.athenet.competition.config;

import static org.mockito.ArgumentMatchers.any;
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
import com.athenet.competition.exception.ForbiddenException;
import com.athenet.competition.mapper.*;
import com.athenet.competition.model.Match;
import com.athenet.competition.service.*;

@WebMvcTest(controllers = {AdminCompetitionController.class, PublicCompetitionController.class,
        PublicMatchController.class, DelegateMatchController.class})
@Import({SecurityConfig.class, CompetitionMapper.class, EnrollmentMapper.class, MatchMapper.class})
class SecurityConfigTest {
    static final String OID = "3f2b9c1e-5a7d-4c1b-9e2f-1d0a8b6c4e21";

    @Autowired MockMvc mvc;
    @MockitoBean CompetitionService competitions;
    @MockitoBean EnrollmentService enrollments;
    @MockitoBean MatchService matches;
    @MockitoBean JwtDecoder decoder;

    void token(String role) {
        when(decoder.decode("test-token")).thenReturn(Jwt.withTokenValue("test-token")
                .header("alg", "RS256").subject("test-user")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
                .claim("roles", List.of(role)).claim("oid", OID).build());
    }

    @Test void publicReadsDoNotNeedToken() throws Exception {
        when(competitions.listForPublic(null, null)).thenReturn(List.of());
        mvc.perform(get("/api/public/competitions")).andExpect(status().isOk());
    }

    @Test void adminRequiresToken() throws Exception {
        mvc.perform(get("/api/admin/competitions/1/matches"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(matches);
    }

    @Test void adminRoleCannotEditMatches() throws Exception {
        token("admin");
        mvc.perform(put("/api/admin/competitions/1/matches/2")
                .header("Authorization", "Bearer test-token").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isForbidden());
        verifyNoInteractions(matches);
    }

    @Test void directorRoleIsMappedFromActualJwtClaims() throws Exception {
        token("director");
        when(matches.listByCompetition(1L)).thenReturn(List.of());
        mvc.perform(get("/api/admin/competitions/1/matches")
                .header("Authorization", "Bearer test-token")).andExpect(status().isOk());
        verify(matches).listByCompetition(1L);
    }

    @Test void directorCannotRegisterResults() throws Exception {
        token("director");
        mvc.perform(put("/api/delegate/matches/2/result")
                .header("Authorization", "Bearer test-token").contentType(MediaType.APPLICATION_JSON)
                .content("{\"homeScore\":2,\"awayScore\":1}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(matches);
    }

    @Test void delegateCannotEditMatchesAsDirector() throws Exception {
        token("delegate");
        mvc.perform(put("/api/admin/competitions/1/matches/2")
                .header("Authorization", "Bearer test-token").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isForbidden());
        verifyNoInteractions(matches);
    }

    @Test void delegateResultUsesOidFromToken() throws Exception {
        token("delegate");
        when(matches.registerResult(2L, OID, 2, 1)).thenReturn(new Match());
        mvc.perform(put("/api/delegate/matches/2/result")
                .header("Authorization", "Bearer test-token").contentType(MediaType.APPLICATION_JSON)
                .content("{\"homeScore\":2,\"awayScore\":1}"))
                .andExpect(status().isOk());
        verify(matches).registerResult(2L, OID, 2, 1);
    }

    @Test void otherDelegateGets403() throws Exception {
        token("delegate");
        when(matches.start(2L, OID)).thenThrow(new ForbiddenException("Solo el delegado asignado"));
        mvc.perform(post("/api/delegate/matches/2/start")
                .header("Authorization", "Bearer test-token")).andExpect(status().isForbidden());
    }

    @Test void invalidScoreReturns400BeforeService() throws Exception {
        token("delegate");
        mvc.perform(put("/api/delegate/matches/2/result")
                .header("Authorization", "Bearer test-token").contentType(MediaType.APPLICATION_JSON)
                .content("{\"homeScore\":-1,\"awayScore\":0}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(matches);
    }

    @Test void invalidDelegateOidReturns400BeforeService() throws Exception {
        token("director");
        mvc.perform(put("/api/admin/competitions/1/matches/2")
                .header("Authorization", "Bearer test-token").contentType(MediaType.APPLICATION_JSON)
                .content("{\"delegateOid\":\"no-es-un-oid\",\"delegateDisplayName\":\"Delegado\"}"))
                .andExpect(status().isBadRequest());
        verify(matches, never()).updateMatch(any(), any(), any());
    }

    @Test void malformedJsonReturns400() throws Exception {
        token("delegate");
        mvc.perform(put("/api/delegate/matches/2/result")
                .header("Authorization", "Bearer test-token").contentType(MediaType.APPLICATION_JSON)
                .content("{"))
                .andExpect(status().isBadRequest());
    }
}
