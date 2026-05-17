package com.lqr.paperragserver.common.model;

import java.util.List;
import java.util.UUID;

/**
 * RAG 流式问答事件。
 *
 * @param type 事件类型：start、delta、done、error
 * @param conversationId 会话 ID
 * @param delta 本次增量文本
 * @param answer 完整回答，仅 done 事件返回
 * @param citations 引用片段，仅 done 事件返回
 * @param message 错误说明，仅 error 事件返回
 */
public record RagStreamEvent(
        String type,
        UUID conversationId,
        String delta,
        String answer,
        List<AnswerCitation> citations,
        String message
) {
    public static RagStreamEvent start(UUID conversationId) {
        return new RagStreamEvent("start", conversationId, null, null, List.of(), null);
    }

    public static RagStreamEvent delta(UUID conversationId, String delta) {
        return new RagStreamEvent("delta", conversationId, delta, null, List.of(), null);
    }

    public static RagStreamEvent done(UUID conversationId, String answer, List<AnswerCitation> citations) {
        return new RagStreamEvent("done", conversationId, null, answer, citations == null ? List.of() : citations, null);
    }

    public static RagStreamEvent error(UUID conversationId, String message) {
        return new RagStreamEvent("error", conversationId, null, null, List.of(), message);
    }
}