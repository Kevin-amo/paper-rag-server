package com.lqr.paperragserver.agent.model;

import com.lqr.paperragserver.common.model.AnswerCitation;

import java.util.List;
import java.util.Map;

public record AgentToolResult(
        String observationSummary,
        String evidenceText,
        List<AnswerCitation> citations,
        Map<String, Object> metadata
) {
    public AgentToolResult {
        citations = citations == null ? List.of() : citations;
        metadata = metadata == null ? Map.of() : metadata;
        observationSummary = observationSummary == null ? "" : observationSummary;
        evidenceText = evidenceText == null ? "" : evidenceText;
    }
}