package com.lqr.paperragserver.agent.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record AgentAskRequest(
        UUID conversationId,
        @NotBlank(message = "目标不能为空") String question,
        @Positive(message = "topK 必须为正数") Integer topK
) {
}