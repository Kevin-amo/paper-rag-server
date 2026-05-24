package com.lqr.paperragserver.agent.service;

import com.lqr.paperragserver.agent.model.AgentActionType;
import com.lqr.paperragserver.agent.model.AgentDecision;
import com.lqr.paperragserver.agent.model.AgentStepTrace;
import com.lqr.paperragserver.agent.model.AgentStreamEvent;
import com.lqr.paperragserver.agent.model.AgentToolResult;
import com.lqr.paperragserver.agent.tool.AgentTool;
import com.lqr.paperragserver.agent.tool.AgentToolRegistry;
import com.lqr.paperragserver.common.model.AnswerCitation;
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
            sink.accept(AgentStreamEvent.thought(conversationId, index, decision.thoughtSummary()));

            if (decision.finish()) {
                String answer = firstNonBlank(decision.answer(), planner.finalAnswer(question, history, steps, observations));
                Map<String, Object> metadata = AgentStepTrace.metadata(steps, extraMetadata);
                return new AgentLoopResult(answer, citations, metadata);
            }

            AgentTool tool = toolRegistry.find(decision.action().value()).orElse(null);
            if (tool == null || decision.action() == AgentActionType.FINISH) {
                String answer = planner.finalAnswer(question, history, steps, observations);
                Map<String, Object> metadata = AgentStepTrace.metadata(steps, extraMetadata);
                return new AgentLoopResult(answer, citations, metadata);
            }

            sink.accept(AgentStreamEvent.toolCall(conversationId, index, tool.name(), decision.actionInput()));
            AgentToolResult result = tool.execute(ownerUserId, decision.actionInput());
            citations.addAll(result.citations());
            observations.add(result.evidenceText());
            mergeMetadata(extraMetadata, result.metadata());
            AgentStepTrace trace = new AgentStepTrace(
                    index,
                    decision.thoughtSummary(),
                    tool.name(),
                    decision.actionInput(),
                    result.observationSummary()
            );
            steps.add(trace);
            sink.accept(AgentStreamEvent.toolResult(conversationId, index, tool.name(), result.observationSummary()));
        }

        String answer = planner.finalAnswer(question, history, steps, observations);
        Map<String, Object> metadata = AgentStepTrace.metadata(steps, extraMetadata);
        metadata.put("stopReason", "MAX_STEPS_REACHED");
        return new AgentLoopResult(answer, citations, metadata);
    }

    private void mergeMetadata(Map<String, Object> target, Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        target.putAll(source);
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first.trim();
    }

    public record AgentLoopResult(
            String answer,
            List<AnswerCitation> citations,
            Map<String, Object> metadata
    ) {
        public AgentLoopResult {
            citations = citations == null ? List.of() : citations;
            metadata = metadata == null ? Map.of() : metadata;
        }
    }
}