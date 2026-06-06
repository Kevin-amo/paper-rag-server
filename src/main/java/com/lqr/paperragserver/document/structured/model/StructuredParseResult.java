package com.lqr.paperragserver.document.structured.model;

import java.util.List;
import java.util.Map;

/**
 * 规则解析、模型补全或合并后的结构化解析快照。
 */
public record StructuredParseResult(
        PaperStructuredContent content,
        Map<String, StructuredFieldEvidence> evidence,
        List<String> missingFields,
        List<String> lowConfidenceFields
) {
}