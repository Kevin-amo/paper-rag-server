package com.lqr.papermind.agent;

import com.lqr.papermind.agent.dto.AgentAskRequest;
import com.lqr.papermind.agent.dto.AgentStreamEvent;
import com.lqr.papermind.agent.application.AgentChatService;
import com.lqr.papermind.agent.core.AgentStep;
import com.lqr.papermind.agent.service.AgentLoop;
import com.lqr.papermind.agent.planning.AgentPlanner;
import com.lqr.papermind.common.model.AnswerCitation;
import com.lqr.papermind.conversation.service.ConversationService;
import com.lqr.papermind.literature.support.LiteratureSearchContextResolver;
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
 * AgentChatService 的流式问答测试，覆盖事件输出、最终回答持久化和异常事件处理。
 */
class AgentServiceTest {

    private final UUID ownerUserId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final ConversationService conversationService = mock(ConversationService.class);
    private final AgentLoop agentLoop = mock(AgentLoop.class);
    private final AgentPlanner planner = mock(AgentPlanner.class);
    private final LiteratureSearchContextResolver literatureSearchContextResolver = mock(LiteratureSearchContextResolver.class);
    private final AgentChatService service = new AgentChatService(conversationService, agentLoop, planner, literatureSearchContextResolver);

    /**
     * 验证流式问答会输出实时事件、拼接最终回答并持久化助手消息。
     */
    @Test
    void streamAnswerShouldEmitLiveDeltasAndPersistFinalAnswer() {
        List<ConversationService.MessageView> history = List.of();
        List<AgentStep> steps = List.of(new AgentStep(
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
        when(agentLoop.run(eq(ownerUserId), eq(conversationId), eq("stream question"), eq(5), eq(history), any(), any()))
                .thenAnswer(invocation -> {
                    Consumer<AgentStreamEvent> sink = invocation.getArgument(6);
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

    /**
     * 验证最终回答流已输出部分内容后失败时，会返回错误事件且不持久化助手消息。
     */
    @Test
    void streamAnswerShouldEmitErrorWhenFinalStreamFailsAfterDelta() {
        List<ConversationService.MessageView> history = List.of();
        List<AgentStep> steps = List.of();
        List<String> observations = List.of("本地论文证据");
        AgentLoop.AgentLoopResult loopResult = new AgentLoop.AgentLoopResult(null, List.of(), Map.of(), steps, observations);
        mockConversation(history);
        when(agentLoop.run(eq(ownerUserId), eq(conversationId), eq("stream question"), eq(null), eq(history), any(), any()))
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

    /**
     * 验证纯外部文献搜索结果仍会进入最终回答生成流程。
     */
    @Test
    void streamAnswerShouldUseFinalGenerationForPureLiteratureSearch() {
        List<ConversationService.MessageView> history = List.of();
        List<AgentStep> steps = List.of(new AgentStep(
                1,
                "用户需要搜索外部文献，我将调用外部文献搜索。",
                "literature_search",
                Map.of("query", "RAG", "limit", 3),
                "外部文献搜索完成，找到 3 篇论文。"
        ));
        List<String> observations = List.of("- [RAG Paper](https://example.org/paper)\n  - 作者：Alice\n  - 年份：2025\n  - 分类：AI");
        Map<String, Object> metadata = Map.of(
                "type", "AGENT_RESULT",
                "steps", steps,
                "literature", Map.of(
                        "type", "LITERATURE_SEARCH_RESULT",
                        "query", "RAG",
                        "items", List.of(Map.of("title", "RAG Paper"), Map.of("title", "Graph RAG"), Map.of("title", "RAG Survey"))
                )
        );
        AgentLoop.AgentLoopResult loopResult = new AgentLoop.AgentLoopResult(null, List.of(), metadata, steps, observations);
        mockConversation(history);
        when(agentLoop.run(eq(ownerUserId), eq(conversationId), eq("stream question"), eq(null), eq(history), any(), any()))
                .thenReturn(loopResult);
        when(planner.finalAnswerStream("stream question", history, steps, observations))
                .thenReturn(Flux.just("LLM 输出的轻量文献列表"));

        List<AgentStreamEvent> events = service.streamAnswer(ownerUserId, new AgentAskRequest(null, "stream question", null))
                .collectList()
                .block(Duration.ofSeconds(2));

        assertThat(events).isNotNull();
        assertThat(events.stream().map(AgentStreamEvent::type)).containsExactly("start", "delta", "done");
        assertThat(events.get(1).delta()).isEqualTo("LLM 输出的轻量文献列表");
        verify(planner).finalAnswerStream("stream question", history, steps, observations);
        verify(conversationService).appendAssistantMessage(ownerUserId, conversationId, "LLM 输出的轻量文献列表", List.of(), metadata);
    }

    /**
     * 构造会话服务的基础桩数据，模拟新会话创建和历史消息读取。
     *
     * @param history 待返回的历史消息
     */
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