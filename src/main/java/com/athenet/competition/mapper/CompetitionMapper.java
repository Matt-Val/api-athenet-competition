package com.athenet.competition.mapper;

import org.springframework.stereotype.Component;

import com.athenet.competition.dto.request.CompetitionRequest;
import com.athenet.competition.dto.response.CompetitionResponse;
import com.athenet.competition.model.Competition;

@Component
public class CompetitionMapper {

    /* 
        Se usa tanto para CompetitionService.create como para armar el objeto
        "changes" que arma el update (CompetitionService.update copia los campos 
        sobre la entidad existente).
    */
    public Competition toEntity(CompetitionRequest request) {
        Competition competition = new Competition();
        competition.setName(request.name());
        competition.setType(request.type());
        competition.setSportId(request.sportId());
        competition.setSeason(request.season());
        competition.setDescription(request.description());
        competition.setCompetitionImg(request.competitionImg());
        competition.setStartDate(request.startDate());
        competition.setEndDate(request.endDate());
        // Defaults si no vienen en el request
        competition.setWinPoints(request.winPoints() != null ? request.winPoints() : 3);
        competition.setDrawPoints(request.drawPoints() != null ? request.drawPoints() : 1);
        competition.setLossPoints(request.lossPoints() != null ? request.lossPoints() : 0);
        return competition;
    }

    public CompetitionResponse toResponse(Competition competition) {
        return new CompetitionResponse(
                competition.getId(),
                competition.getName(),
                competition.getType(),
                competition.getSportId(),
                competition.getSeason(),
                competition.getDescription(),
                competition.getCompetitionImg(),
                competition.getStatus(),
                competition.getStartDate(),
                competition.getEndDate(),
                competition.getWinPoints(),
                competition.getDrawPoints(),
                competition.getLossPoints()
        );
    }
}
