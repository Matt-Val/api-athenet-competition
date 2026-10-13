package com.athenet.competition.mapper;

import org.springframework.stereotype.Component;

import com.athenet.competition.dto.response.MatchResponse;
import com.athenet.competition.model.Match;

@Component
public class MatchMapper {

    public MatchResponse toResponse(Match match) {
        return new MatchResponse(
                match.getId(),
                match.getCompetitionId(),
                match.getEventId(),
                match.getEventInternalId(),
                match.getDate(),
                match.getTime(),
                match.getHomeTeamId(),
                match.getAwayTeamId(),
                match.getStatus(),
                match.getHomeScore(),
                match.getAwayScore(),
                match.isSimulated(),
                match.getVersion()
        );
    }
}
