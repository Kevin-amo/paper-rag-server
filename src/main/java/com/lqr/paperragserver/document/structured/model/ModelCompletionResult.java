package com.lqr.paperragserver.document.structured.model;

import java.util.List;

/**
 * 模型补全结果与原始输出。
 */
public record ModelCompletionResult(
        StructuredParseResult result,
        String rawModelOutput,
        String errorMessage
) {
    public static ModelCompletionResult empty() {
        PaperStructuredContent content = PaperStructuredContentSupport.emptyContent();
        return new ModelCompletionResult(
                new StructuredParseResult(content, PaperStructuredContentSupport.emptyEvidence("MODEL"), PaperStructuredContentSupport.emptyFields(content), List.of()),
                null,
                null
        );
    }
}