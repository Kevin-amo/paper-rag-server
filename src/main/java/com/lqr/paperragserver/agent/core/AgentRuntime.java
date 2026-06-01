package com.lqr.paperragserver.agent.core;

import com.lqr.paperragserver.agent.paper.CitationNormalizer;
import com.lqr.paperragserver.agent.planning.AgentPlanner;
import com.lqr.paperragserver.agent.tool.AgentTool;
import com.lqr.paperragserver.agent.tool.AgentToolRegistry;
import com.lqr.paperragserver.agent.tool.AgentToolResult;
import com.lqr.paperragserver.common.logging.LogSanitizer;
import com.lqr.paperragserver.common.model.AnswerCitation;
import com.lqr.paperragserver.conversation.service.ConversationService;
import com.lqr.paperragserver.literature.model.LiteratureSearchContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRuntime {

    private static final int MAX_STEPS = 5;

    private final AgentPlanner planner;
    private final AgentToolRegistry toolRegistry;
    private final CitationNormalizer citationNormalizer;

    public AgentRuntimeResult run(UUID ownerUserId,
                                  UUID conversationId,
                                  String question,
                                  Integer topK,
                                  List<ConversationService.MessageView> history,
                                  LiteratureSearchContext lastLiteratureContext,
                                  Consumer<AgentRuntimeEvent> sink) {
        List<AgentStep> steps = new ArrayList<>();
        List<String> observations = new ArrayList<>();
        List<AnswerCitation> citations = new ArrayList<>();
        Map<String, Object> extraMetadata = new LinkedHashMap<>();

        log.info("agent.loop.start ownerUserId={} conversationId={} questionLength={} questionExcerpt={} topK={} historyCount={} hasLiteratureContext={} maxSteps={}",
                ownerUserId, conversationId, textLength(question), LogSanitizer.safeExcerpt(question, 160), topK, size(history), lastLiteratureContext != null, MAX_STEPS);
        for (int index = 1; index <= MAX_STEPS; index++) {
            sink.accept(AgentRuntimeEvent.step(index));
            AgentDecision decision = planner.decide(question, history, lastLiteratureContext, steps, observations, topK);
            String thoughtSummary = deterministicThoughtSummary(decision.action());
            log.info("agent.plan.step conversationId={} step={} action={} finish={} actionInputSummary={}",
                    conversationId, index, decision.action(), decision.finish(), LogSanitizer.safeActionInput(decision.actionInput()));

            if (decision.finish()) {
                log.info("agent.loop.done ownerUserId={} conversationId={} stopReason=FINISH stepCount={} citationRawCount={} observationCount={}",
                        ownerUserId, conversationId, steps.size(), citations.size(), observations.size());
                return result(decision.answer(), citations, topK, metadata(steps, extraMetadata), steps, observations);
            }

            AgentTool tool = toolRegistry.find(decision.action().value()).orElse(null);
            if (tool == null || decision.action() == AgentActionType.FINISH) {
                log.warn("agent.loop.stop ownerUserId={} conversationId={} step={} stopReason=NO_TOOL action={}",
                        ownerUserId, conversationId, index, decision.action());
                return result(decision.answer(), citations, topK, metadata(steps, extraMetadata), steps, observations);
            }
            if (hasRepeatedAction(steps, tool.name(), decision.actionInput()) && !observations.isEmpty()) {
                Map<String, Object> metadata = metadata(steps, extraMetadata);
                metadata.put("stopReason", "REPEATED_ACTION");
                log.warn("agent.loop.stop ownerUserId={} conversationId={} step={} stopReason=REPEATED_ACTION toolName={} actionInputSummary={}",
                        ownerUserId, conversationId, index, tool.name(), LogSanitizer.safeActionInput(decision.actionInput()));
                return result(null, citations, topK, metadata, steps, observations);
            }

            sink.accept(AgentRuntimeEvent.thought(index, thoughtSummary));
            sink.accept(AgentRuntimeEvent.toolCall(index, tool.name(), decision.actionInput()));
            long toolStartNanos = System.nanoTime();
            log.info("agent.tool.start ownerUserId={} conversationId={} step={} toolName={} actionInputSummary={}",
                    ownerUserId, conversationId, index, tool.name(), LogSanitizer.safeActionInput(decision.actionInput()));
            AgentToolResult result = executeTool(ownerUserId, tool, decision.actionInput());
            log.info("agent.tool.done ownerUserId={} conversationId={} step={} toolName={} observationLength={} citationCount={} metadataKeys={} costMs={}",
                    ownerUserId, conversationId, index, tool.name(), textLength(result.observationSummary()), result.citations().size(), metadataKeys(result.metadata()), elapsedMs(toolStartNanos));
            citations.addAll(result.citations());
            observations.add(result.evidenceText());
            mergeMetadata(extraMetadata, result.metadata());
            AgentStep trace = new AgentStep(
                    index,
                    thoughtSummary,
                    tool.name(),
                    decision.actionInput(),
                    result.observationSummary()
            );
            steps.add(trace);
            sink.accept(AgentRuntimeEvent.toolResult(index, tool.name(), result.observationSummary()));
            if (isToolUnavailable(result.metadata())) {
                Map<String, Object> metadata = metadata(steps, extraMetadata);
                metadata.put("stopReason", "TOOL_UNAVAILABLE");
                log.warn("agent.loop.stop ownerUserId={} conversationId={} step={} stopReason=TOOL_UNAVAILABLE toolName={}",
                        ownerUserId, conversationId, index, tool.name());
                return result(null, citations, topK, metadata, steps, observations);
            }
        }

        Map<String, Object> metadata = metadata(steps, extraMetadata);
        metadata.put("stopReason", "MAX_STEPS_REACHED");
        log.warn("agent.loop.stop ownerUserId={} conversationId={} stopReason=MAX_STEPS_REACHED stepCount={} citationRawCount={} observationCount={}",
                ownerUserId, conversationId, steps.size(), citations.size(), observations.size());
        return result(null, citations, topK, metadata, steps, observations);
    }

    private Map<String, Object> metadata(List<AgentStep> steps, Map<String, Object> extraMetadata) {
        return new LinkedHashMap<>(AgentStep.metadata(steps, extraMetadata));
    }

    private AgentRuntimeResult result(String draftAnswer,
                                      List<AnswerCitation> citations,
                                      Integer topK,
                                      Map<String, Object> metadata,
                                      List<AgentStep> steps,
                                      List<String> observations) {
        return new AgentRuntimeResult(draftAnswer, citationNormalizer.normalize(citations, topK), metadata, steps, observations);
    }

    private AgentToolResult executeTool(UUID ownerUserId, AgentTool tool, Map<String, Object> actionInput) {
        try {
            return tool.execute(ownerUserId, actionInput);
        } catch (RuntimeException ex) {
            log.warn("agent.tool.failed ownerUserId={} toolName={} actionInputSummary={}",
                    ownerUserId, tool.name(), LogSanitizer.safeActionInput(actionInput), ex);
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

    private boolean hasRepeatedAction(List<AgentStep> steps, String action, Map<String, Object> actionInput) {
        if (steps == null || steps.isEmpty()) {
            return false;
        }
        AgentStep lastStep = steps.get(steps.size() - 1);
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

    private int textLength(String text) {
        return text == null ? 0 : text.length();
    }

    private int size(List<?> items) {
        return items == null ? 0 : items.size();
    }

    private java.util.Set<String> metadataKeys(Map<String, Object> metadata) {
        return metadata == null ? java.util.Set.of() : new java.util.TreeSet<>(metadata.keySet());
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private String deterministicThoughtSummary(AgentActionType action) {
        return switch (action) {
            case LITERATURE_SEARCH -> "用户需要搜索外部文献，我将调用外部文献搜索。";
            case LOCAL_PAPER_RETRIEVAL -> "用户需要分析已上传文档，我将检索本地论文知识库。";
            case FINISH -> "已有工具结果足够回答，我将整理最终回复。";
        };
    }

    public record AgentRuntimeResult(
            String draftAnswer,
            List<AnswerCitation> citations,
            Map<String, Object> metadata,
            List<AgentStep> steps,
            List<String> observations
    ) {
        public AgentRuntimeResult {
            citations = citations == null ? List.of() : List.copyOf(citations);
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
            steps = steps == null ? List.of() : List.copyOf(steps);
            observations = observations == null ? List.of() : List.copyOf(observations);
        }
    }

    public record AgentRuntimeEvent(
            Type type,
            int step,
            String thoughtSummary,
            String toolName,
            Map<String, Object> actionInput,
            String observationSummary
    ) {
        public AgentRuntimeEvent {
            actionInput = actionInput == null ? Map.of() : actionInput;
        }

        static AgentRuntimeEvent step(int step) {
            return new AgentRuntimeEvent(Type.STEP, step, null, null, Map.of(), null);
        }

        static AgentRuntimeEvent thought(int step, String thoughtSummary) {
            return new AgentRuntimeEvent(Type.THOUGHT, step, thoughtSummary, null, Map.of(), null);
        }

        static AgentRuntimeEvent toolCall(int step, String toolName, Map<String, Object> actionInput) {
            return new AgentRuntimeEvent(Type.TOOL_CALL, step, null, toolName, actionInput, null);
        }

        static AgentRuntimeEvent toolResult(int step, String toolName, String observationSummary) {
            return new AgentRuntimeEvent(Type.TOOL_RESULT, step, null, toolName, Map.of(), observationSummary);
        }

        public enum Type {
            STEP,
            THOUGHT,
            TOOL_CALL,
            TOOL_RESULT
        }
    }
}