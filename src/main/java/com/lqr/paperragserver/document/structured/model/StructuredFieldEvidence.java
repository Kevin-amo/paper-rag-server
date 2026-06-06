package com.lqr.paperragserver.document.structured.model;

/**
 * 结构化字段的来源与可信度信息。
 */
public record StructuredFieldEvidence(
        String fieldName,
        String source,
        double confidence,
        boolean missing,
        String evidence
) {
}