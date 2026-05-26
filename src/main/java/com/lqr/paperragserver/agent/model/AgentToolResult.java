package com.lqr.paperragserver.agent.model;

import com.lqr.paperragserver.common.model.AnswerCitation;

import java.util.List;
import java.util.Map;

/**
 * 智能体工具执行结果，承载观察摘要、证据文本、引用列表和结构化元数据。
 */
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