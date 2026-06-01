package com.lqr.paperragserver.agent.service;

import com.lqr.paperragserver.agent.core.AgentRuntime;
import com.lqr.paperragserver.agent.core.AgentStep;
import com.lqr.paperragserver.agent.dto.AgentStreamEvent;
import com.lqr.paperragserver.common.model.AnswerCitation;
import com.lqr.paperragserver.conversation.service.ConversationService;
import com.lqr.paperragserver.literature.model.LiteratureSearchContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class AgentLoop {

    private final AgentRuntime runtime;

    public AgentLoopResult run(UUID ownerUserId,
                               UUID conversationId,
                               String question,
                               Integer topK,
                               List<ConversationService.MessageView> history,
                               LiteratureSearchContext lastLiteratureContext,
                               Consumer<AgentStreamEvent> sink) {
        AgentRuntime.AgentRuntimeResult result = runtime.run(
                ownerUserId,
                conversationId,
                question,
                topK,
                history,
                lastLiteratureContext,
                event -> sink.accept(toStreamEvent(conversationId, event))
        );
        return new AgentLoopResult(
                result.draftAnswer(),
                result.citations(),
                result.metadata(),
                result.steps(),
                result.observations()
        );
    }

    private AgentStreamEvent toStreamEvent(UUID conversationId, AgentRuntime.AgentRuntimeEvent event) {
        return switch (event.type()) {
            case STEP -> AgentStreamEvent.step(conversationId, event.step());
            case THOUGHT -> AgentStreamEvent.thought(conversationId, event.step(), event.thoughtSummary());
            case TOOL_CALL -> AgentStreamEvent.toolCall(conversationId, event.step(), event.toolName(), event.actionInput());
            case TOOL_RESULT -> AgentStreamEvent.toolResult(conversationId, event.step(), event.toolName(), event.observationSummary());
        };
    }

    public record AgentLoopResult(
            String draftAnswer,
            List<AnswerCitation> citations,
            Map<String, Object> metadata,
            List<AgentStep> steps,
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