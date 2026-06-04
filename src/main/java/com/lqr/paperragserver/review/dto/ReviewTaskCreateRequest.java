package com.lqr.paperragserver.review.dto;

public record ReviewTaskCreateRequest(
        String sourceId,
        String title
) {
}