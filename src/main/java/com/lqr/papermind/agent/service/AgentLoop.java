package com.lqr.papermind.agent.service;

import com.lqr.papermind.agent.core.AgentRuntime;
import com.lqr.papermind.agent.core.AgentStep;
import com.lqr.papermind.agent.dto.AgentStreamEvent;
import com.lqr.papermind.common.model.AnswerCitation;
import com.lqr.papermind.conversation.service.ConversationService;
import com.lqr.papermind.literature.model.LiteratureSearchContext;
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

    /**
     * 执行一次智能体循环，并将运行时事件转换为前端流式事件。
     *
     * @param ownerUserId           当前用户标识
     * @param conversationId        当前会话标识
     * @param question              用户当前问题
     * @param topK                  本地检索片段数量配置
     * @param history               最近会话历史
     * @param lastLiteratureContext 最近一次文献搜索上下文
     * @param sink                  流式事件消费者
     * @return 智能体循环结果
     */
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

    /**
     * 将运行时内部事件转换为对外暴露的流式响应事件。
     *
     * @param conversationId 当前会话标识
     * @param event          运行时内部事件
     * @return 前端流式事件
     */
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
        /**
         * 规范化智能体循环结果，确保集合字段对下游只读且非空。
         */
        public AgentLoopResult {
            citations = citations == null ? List.of() : List.copyOf(citations);
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
            steps = steps == null ? List.of() : List.copyOf(steps);
            observations = observations == null ? List.of() : List.copyOf(observations);
        }
    }
}