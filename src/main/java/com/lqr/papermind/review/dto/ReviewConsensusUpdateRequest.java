package com.lqr.papermind.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ReviewConsensusUpdateRequest(
        @Min(0) @Max(100) Integer finalScore,
        String finalRecommendation
) {
}
