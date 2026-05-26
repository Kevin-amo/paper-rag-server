package com.lqr.paperragserver.agent;

import com.lqr.paperragserver.agent.dto.AgentAskRequest;
import com.lqr.paperragserver.agent.dto.AgentStreamEvent;
import com.lqr.paperragserver.agent.model.AgentStepTrace;
import com.lqr.paperragserver.agent.service.AgentLoop;
import com.lqr.paperragserver.agent.service.AgentPlanner;
import com.lqr.paperragserver.agent.service.AgentService;
import com.lqr.paperragserver.common.model.AnswerCitation;
import com.lqr.paperragserver.conversation.service.ConversationService;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentService 的流式问答测试，覆盖事件输出、最终回答持久化和异常事件处理。
 */
class AgentServiceTest {

    private final UUID ownerUserId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final ConversationService conversationService = mock(ConversationService.class);
    private final AgentLoop agentLoop = mock(AgentLoop.class);
    private final AgentPlanner planner = mock(AgentPlanner.class);
    private final AgentService service = new AgentService(conversationService, agentLoop, planner);

    @Test
    void streamAnswerShouldEmitLiveDeltasAndPersistFinalAnswer() {
        List<ConversationService.MessageView> history = List.of();
        List<AgentStepTrace> steps = List.of(new AgentStepTrace(
                1,
                "先检索本地论文。",
                "local_paper_retrieval",
                Map.of("query", "stream question"),
                "找到 1 个相关片段。"
        ));
        List<String> observations = List.of("本地论文证据");
        List<AnswerCitation> citations = List.of(new AnswerCitation("source-1", "chunk-1", 1, "Paper A", "excerpt", 0.9));
        Map<String, Object> metadata = Map.of("type", "AGENT_RESULT", "steps", List.of());
        AgentLoop.AgentLoopResult loopResult = new AgentLoop.AgentLoopResult(null, citations, metadata, steps, observations);
        mockConversation(history);
        when(agentLoop.run(eq(ownerUserId), eq(conversationId), eq("stream question"), eq(5), eq(history), any()))
                .thenAnswer(invocation -> {
                    Consumer<AgentStreamEvent> sink = invocation.getArgument(5);
                    sink.accept(AgentStreamEvent.step(conversationId, 1));
                    sink.accept(AgentStreamEvent.thought(conversationId, 1, "先检索本地论文。"));
                    sink.accept(AgentStreamEvent.toolCall(conversationId, 1, "local_paper_retrieval", Map.of("query", "stream question")));
                    sink.accept(AgentStreamEvent.toolResult(conversationId, 1, "local_paper_retrieval", "找到 1 个相关片段。"));
                    return loopResult;
                });
        when(planner.finalAnswerStream("stream question", history, steps, observations))
                .thenReturn(Flux.just("hello ", "world"));

        List<AgentStreamEvent> events = service.streamAnswer(ownerUserId, new AgentAskRequest(null, "stream question", 5))
                .collectList()
                .block(Duration.ofSeconds(2));

        assertThat(events).isNotNull();
        assertThat(events.stream().map(AgentStreamEvent::type)).containsExactly(
                "start",
                "step",
                "thought",
                "tool_call",
                "tool_result",
                "delta",
                "delta",
                "done"
        );
        assertThat(events.get(5).delta()).isEqualTo("hello ");
        assertThat(events.get(6).delta()).isEqualTo("world");
        assertThat(events.get(7).answer()).isEqualTo("hello world");
        assertThat(events.get(7).citations()).isEqualTo(citations);
        assertThat(events.get(7).metadata()).isEqualTo(metadata);
        verify(conversationService).appendAssistantMessage(ownerUserId, conversationId, "hello world", citations, metadata);
    }

    @Test
    void streamAnswerShouldEmitErrorWhenFinalStreamFailsAfterDelta() {
        List<ConversationService.MessageView> history = List.of();
        List<AgentStepTrace> steps = List.of();
        List<String> observations = List.of("本地论文证据");
        AgentLoop.AgentLoopResult loopResult = new AgentLoop.AgentLoopResult(null, List.of(), Map.of(), steps, observations);
        mockConversation(history);
        when(agentLoop.run(eq(ownerUserId), eq(conversationId), eq("stream question"), eq(null), eq(history), any()))
                .thenReturn(loopResult);
        when(planner.finalAnswerStream("stream question", history, steps, observations))
                .thenReturn(Flux.concat(Flux.just("partial"), Flux.error(new RuntimeException("model stream failed"))));

        List<AgentStreamEvent> events = service.streamAnswer(ownerUserId, new AgentAskRequest(null, "stream question", null))
                .collectList()
                .block(Duration.ofSeconds(2));

        assertThat(events).isNotNull();
        assertThat(events.stream().map(AgentStreamEvent::type)).containsExactly("start", "delta", "error");
        assertThat(events.get(1).delta()).isEqualTo("partial");
        assertThat(events.get(2).message()).contains("model stream failed");
        verify(conversationService, never()).appendAssistantMessage(eq(ownerUserId), eq(conversationId), any(), any(), any());
    }

    private void mockConversation(List<ConversationService.MessageView> history) {
        when(conversationService.createConversation(ownerUserId, "stream question"))
                .thenReturn(new ConversationService.ConversationView(
                        conversationId,
                        ownerUserId,
                        "stream question",
                        OffsetDateTime.now(),
                        OffsetDateTime.now()
                ));
        when(conversationService.recentMessages(ownerUserId, conversationId, ConversationService.DEFAULT_HISTORY_MESSAGE_LIMIT))
                .thenReturn(history);
    }
}