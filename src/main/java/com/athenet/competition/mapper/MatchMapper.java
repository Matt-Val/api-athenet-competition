package com.athenet.competition.mapper;

import org.springframework.stereotype.Component;

import com.athenet.competition.dto.request.MatchUpdateRequest;
import com.athenet.competition.dto.response.MatchResponse;
import com.athenet.competition.dto.response.TeamMatchResponse;
import com.athenet.competition.dto.response.TeamMatchResponse.CompetitionSummary;
import com.athenet.competition.dto.response.TeamMatchResponse.TeamScore;
import com.athenet.competition.model.Competition;
import com.athenet.competition.model.Enrollment;
import com.athenet.competition.model.Match;

@Component
public class MatchMapper {

    // Objeto "changes" para MatchService.updateMatch, que copia los campos sobre el partido existente
    public Match toEntity(MatchUpdateRequest request) {
        Match match = new Match();
        match.setDate(request.date());
        match.setTime(request.time());
        match.setLocation(request.location());
        match.setAddress(request.address());
        match.setHomeTeamId(request.homeTeamId());
        match.setAwayTeamId(request.awayTeamId());
        match.setDelegateOid(request.delegateOid());
        match.setDelegateDisplayName(request.delegateDisplayName());
        return match;
    }

    public MatchResponse toResponse(Match match) {
        return new MatchResponse(
                match.getId(),
                match.getCompetitionId(),
                match.getRound(),
                match.getPosition(),
                match.getDate(),
                match.getTime(),
                match.getLocation(),
                match.getAddress(),
                match.getHomeTeamId(),
                match.getAwayTeamId(),
                match.getDelegateOid(),
                match.getDelegateDisplayName(),
                match.getStatus(),
                match.getHomeScore(),
                match.getAwayScore(),
                match.getVersion()
        );
    }

    public TeamMatchResponse toTeamResponse(Match match, Competition competition, Enrollment home, Enrollment away) {
        return new TeamMatchResponse(
                match.getId(),
                new CompetitionSummary(competition.getId(), competition.getName(),
                        competition.getSeason(), competition.getCompetitionImg()),
                match.getRound(),
                match.getDate(),
                match.getTime(),
                match.getLocation(),
                match.getAddress(),
                match.getStatus(),
                teamScore(home, match.getHomeScore()),
                teamScore(away, match.getAwayScore())
        );
    }

    private static TeamScore teamScore(Enrollment team, Integer score) {
        return team == null ? null : new TeamScore(team.getTeamId(), team.getTeamName(), team.getTeamLogoUrl(), score);
    }
}
