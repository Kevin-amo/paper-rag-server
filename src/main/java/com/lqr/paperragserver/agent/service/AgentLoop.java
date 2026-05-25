package com.lqr.paperragserver.agent.service;

import com.lqr.paperragserver.agent.model.AgentActionType;
import com.lqr.paperragserver.agent.model.AgentDecision;
import com.lqr.paperragserver.agent.model.AgentStepTrace;
import com.lqr.paperragserver.agent.model.AgentStreamEvent;
import com.lqr.paperragserver.agent.model.AgentToolResult;
import com.lqr.paperragserver.agent.tool.AgentTool;
import com.lqr.paperragserver.agent.tool.AgentToolRegistry;
import com.lqr.paperragserver.common.model.AnswerCitation;
import com.lqr.paperragserver.config.RagProperties;
import com.lqr.paperragserver.conversation.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class AgentLoop {

    private static final int MAX_STEPS = 5;

    private final AgentPlanner planner;
    private final AgentToolRegistry toolRegistry;
    private final RagProperties ragProperties;

    public AgentLoopResult run(UUID ownerUserId,
                               UUID conversationId,
                               String question,
                               Integer topK,
                               List<ConversationService.MessageView> history,
                               Consumer<AgentStreamEvent> sink) {
        List<AgentStepTrace> steps = new ArrayList<>();
        List<String> observations = new ArrayList<>();
        List<AnswerCitation> citations = new ArrayList<>();
        Map<String, Object> extraMetadata = new LinkedHashMap<>();

        for (int index = 1; index <= MAX_STEPS; index++) {
            sink.accept(AgentStreamEvent.step(conversationId, index));
            AgentDecision decision = planner.decide(question, history, steps, observations, topK);
            String thoughtSummary = deterministicThoughtSummary(decision.action());

            if (decision.finish()) {
                return result(decision.answer(), citations, topK, metadata(steps, extraMetadata), steps, observations);
            }

            AgentTool tool = toolRegistry.find(decision.action().value()).orElse(null);
            if (tool == null || decision.action() == AgentActionType.FINISH) {
                return result(decision.answer(), citations, topK, metadata(steps, extraMetadata), steps, observations);
            }
            if (hasRepeatedAction(steps, tool.name(), decision.actionInput()) && !observations.isEmpty()) {
                Map<String, Object> metadata = metadata(steps, extraMetadata);
                metadata.put("stopReason", "REPEATED_ACTION");
                return result(null, citations, topK, metadata, steps, observations);
            }

            sink.accept(AgentStreamEvent.thought(conversationId, index, thoughtSummary));
            sink.accept(AgentStreamEvent.toolCall(conversationId, index, tool.name(), decision.actionInput()));
            AgentToolResult result = executeTool(ownerUserId, tool, decision.actionInput());
            citations.addAll(result.citations());
            observations.add(result.evidenceText());
            mergeMetadata(extraMetadata, result.metadata());
            AgentStepTrace trace = new AgentStepTrace(
                    index,
                    thoughtSummary,
                    tool.name(),
                    decision.actionInput(),
                    result.observationSummary()
            );
            steps.add(trace);
            sink.accept(AgentStreamEvent.toolResult(conversationId, index, tool.name(), result.observationSummary()));
            if (isToolUnavailable(result.metadata())) {
                Map<String, Object> metadata = metadata(steps, extraMetadata);
                metadata.put("stopReason", "TOOL_UNAVAILABLE");
                return result(null, citations, topK, metadata, steps, observations);
            }
        }

        Map<String, Object> metadata = metadata(steps, extraMetadata);
        metadata.put("stopReason", "MAX_STEPS_REACHED");
        return result(null, citations, topK, metadata, steps, observations);
    }

    private Map<String, Object> metadata(List<AgentStepTrace> steps, Map<String, Object> extraMetadata) {
        return new LinkedHashMap<>(AgentStepTrace.metadata(steps, extraMetadata));
    }

    private AgentLoopResult result(String draftAnswer,
                                   List<AnswerCitation> citations,
                                   Integer topK,
                                   Map<String, Object> metadata,
                                   List<AgentStepTrace> steps,
                                   List<String> observations) {
        return new AgentLoopResult(draftAnswer, normalizeCitations(citations, topK), metadata, steps, observations);
    }

    private List<AnswerCitation> normalizeCitations(List<AnswerCitation> citations, Integer topK) {
        if (citations == null || citations.isEmpty()) {
            return List.of();
        }
        Map<String, AnswerCitation> citationByKey = new LinkedHashMap<>();
        for (AnswerCitation citation : citations) {
            if (citation == null) {
                continue;
            }
            String key = citationKey(citation);
            AnswerCitation existing = citationByKey.get(key);
            if (existing == null || citation.rankScore() > existing.rankScore()) {
                citationByKey.put(key, citation);
            }
        }
        int limit = citationLimit(topK);
        return citationByKey.values().stream()
                .sorted((left, right) -> Double.compare(right.rankScore(), left.rankScore()))
                .limit(limit)
                .toList();
    }

    private int citationLimit(Integer topK) {
        if (topK != null && topK > 0) {
            return topK;
        }
        return Math.max(1, ragProperties.defaultTopK());
    }

    private String citationKey(AnswerCitation citation) {
        if (hasText(citation.chunkId())) {
            return "chunk:" + citation.chunkId();
        }
        return "source-chunk:" + nullToEmpty(citation.sourceId()) + ':' + citation.chunkIndex();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private AgentToolResult executeTool(UUID ownerUserId, AgentTool tool, Map<String, Object> actionInput) {
        try {
            return tool.execute(ownerUserId, actionInput);
        } catch (RuntimeException ex) {
            String message = userSafeMessage(ex);
            return new AgentToolResult(
                    tool.name() + " 执行失败：" + message,
                    tool.name() + " 工具暂不可用，未能获取新的检索结果。原因：" + message,
                    List.of(),
                    Map.of("toolUnavailable", true, "toolErrorMessage", message)
            );
        }
    }

    private boolean isToolUnavailable(Map<String, Object> metadata) {
        return metadata != null && Boolean.TRUE.equals(metadata.get("toolUnavailable"));
    }

    private String userSafeMessage(RuntimeException ex) {
        if (ex == null || ex.getMessage() == null || ex.getMessage().isBlank()) {
            return "服务暂不可用，请稍后重试";
        }
        return ex.getMessage().trim();
    }

    private boolean hasRepeatedAction(List<AgentStepTrace> steps, String action, Map<String, Object> actionInput) {
        if (steps == null || steps.isEmpty()) {
            return false;
        }
        AgentStepTrace lastStep = steps.get(steps.size() - 1);
        if (action.equals(lastStep.action())) {
            return true;
        }
        return steps.stream().anyMatch(step -> action.equals(step.action()) && step.actionInput().equals(actionInput));
    }

    private void mergeMetadata(Map<String, Object> target, Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        target.putAll(source);
    }

    private String deterministicThoughtSummary(AgentActionType action) {
        return switch (action) {
            case LITERATURE_SEARCH -> "用户需要搜索外部文献，我将调用外部文献搜索。";
            case LOCAL_PAPER_RETRIEVAL -> "用户需要分析已上传文档，我将检索本地论文知识库。";
            case FINISH -> "已有工具结果足够回答，我将整理最终回复。";
        };
    }

    public record AgentLoopResult(
            String draftAnswer,
            List<AnswerCitation> citations,
            Map<String, Object> metadata,
            List<AgentStepTrace> steps,
            List<String> observations
    ) {
        public AgentLoopResult {
            citations = citations == null ? List.of() : List.copyOf(citations);
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
            steps = steps == null ? List.of() : List.copyOf(steps);
            observations = observations == null ? List.of() : List.copyOf(observations);
        }
    }
}
