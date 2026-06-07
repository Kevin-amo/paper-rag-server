package com.lqr.paperragserver.review.dto;

import jakarta.validation.constraints.Pattern;

public record ReviewRiskUpdateRequest(
        @Pattern(regexp = "OPEN|CONFIRMED|IGNORED|RESOLVED") String status,
        String reviewerNote
) {
}
