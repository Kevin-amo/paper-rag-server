package com.lqr.paperragserver.agent.model;

import com.lqr.paperragserver.common.model.AnswerCitation;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AgentStreamEvent(
        String type,
        UUID conversationId,
        Integer step,
        String thought,
        String toolName,
        Map<String, Object> toolInput,
        String observation,
        String delta,
        String answer,
        List<AnswerCitation> citations,
        Map<String, Object> metadata,
        String message
) {
    public AgentStreamEvent {
        citations = citations == null ? List.of() : citations;
        metadata = metadata == null ? Map.of() : metadata;
        toolInput = toolInput == null ? Map.of() : toolInput;
    }

    public static AgentStreamEvent start(UUID conversationId) {
        return new AgentStreamEvent("start", conversationId, null, null, null, Map.of(), null, null, null, List.of(), Map.of(), null);
    }

    public static AgentStreamEvent step(UUID conversationId, int step) {
        return new AgentStreamEvent("step", conversationId, step, null, null, Map.of(), null, null, null, List.of(), Map.of(), null);
    }

    public static AgentStreamEvent thought(UUID conversationId, int step, String thought) {
        return new AgentStreamEvent("thought", conversationId, step, thought, null, Map.of(), null, null, null, List.of(), Map.of(), null);
    }

    public static AgentStreamEvent toolCall(UUID conversationId, int step, String toolName, Map<String, Object> toolInput) {
        return new AgentStreamEvent("tool_call", conversationId, step, null, toolName, toolInput, null, null, null, List.of(), Map.of(), null);
    }

    public static AgentStreamEvent toolResult(UUID conversationId, int step, String toolName, String observation) {
        return new AgentStreamEvent("tool_result", conversationId, step, null, toolName, Map.of(), observation, null, null, List.of(), Map.of(), null);
    }

    public static AgentStreamEvent delta(UUID conversationId, String delta) {
        return new AgentStreamEvent("delta", conversationId, null, null, null, Map.of(), null, delta, null, List.of(), Map.of(), null);
    }

    public static AgentStreamEvent done(UUID conversationId, String answer, List<AnswerCitation> citations, Map<String, Object> metadata) {
        return new AgentStreamEvent("done", conversationId, null, null, null, Map.of(), null, null, answer, citations, metadata, null);
    }

    public static AgentStreamEvent error(UUID conversationId, String message) {
        return new AgentStreamEvent("error", conversationId, null, null, null, Map.of(), null, null, null, List.of(), Map.of(), message);
    }
}