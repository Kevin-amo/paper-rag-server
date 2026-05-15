package com.lqr.paperragserver.common.model;

import java.util.List;
import java.util.UUID;

/**
 * RAG 问答返回结果。
 *
 * @param answer 大模型生成的最终回答文本
 * @param citations 回答引用的文档片段列表，用于前端展示来源依据
 * @param conversationId 本次问答所属会话 ID
 */
public record RagAnswer(
        String answer,
        List<AnswerCitation> citations,
        UUID conversationId
) {
    public RagAnswer(String answer, List<AnswerCitation> citations) {
        this(answer, citations, null);
    }
}