package com.athenet.competition.mapper;

import org.springframework.stereotype.Component;

import com.athenet.competition.dto.response.EnrollmentResponse;
import com.athenet.competition.model.Enrollment;

@Component
public class EnrollmentMapper {

    public EnrollmentResponse toResponse(Enrollment enrollment) {
        return new EnrollmentResponse(
                enrollment.getId(),
                enrollment.getCompetitionId(),
                enrollment.getTeamId(),
                enrollment.getInstitutionId(),
                enrollment.getTeamName(),
                enrollment.getTeamLogoUrl()
        );
    }
}
