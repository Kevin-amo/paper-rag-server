package com.lqr.papermind.review.dto;

public record ReviewTaskCreateRequest(
        String sourceId,
        String title
) {
}