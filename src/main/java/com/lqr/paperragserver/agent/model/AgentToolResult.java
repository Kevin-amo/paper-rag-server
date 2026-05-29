package com.lqr.paperragserver.agent.model;

import com.lqr.paperragserver.common.model.AnswerCitation;

import java.util.List;
import java.util.Map;

/**
 * 智能体工具执行结果，承载观察摘要、证据文本、引用列表和结构化元数据。
 *
 * @param observationSummary 面向执行轨迹展示的简短观察
 * @param evidenceText       可供最终回答生成器使用的证据文本
 * @param citations          可展示给用户的引用来源
 * @param metadata           工具产生的结构化扩展信息
 */
public record AgentToolResult(
        String observationSummary,
        String evidenceText,
        List<AnswerCitation> citations,
        Map<String, Object> metadata
) {
    /**
     * 规范化工具结果字段，保证调用方始终可以安全读取集合和文本内容。
     */
    public AgentToolResult {
        citations = citations == null ? List.of() : citations;
        metadata = metadata == null ? Map.of() : metadata;
        observationSummary = observationSummary == null ? "" : observationSummary;
        evidenceText = evidenceText == null ? "" : evidenceText;
    }
}