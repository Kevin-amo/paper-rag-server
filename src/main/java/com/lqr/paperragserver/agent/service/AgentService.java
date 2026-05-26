package com.lqr.paperragserver.agent.service;

import com.lqr.paperragserver.agent.dto.AgentAskRequest;
import com.lqr.paperragserver.agent.dto.AgentStreamEvent;
import com.lqr.paperragserver.conversation.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 论文智能体对话服务，负责创建或接续会话、驱动执行循环，并以流式事件输出回答过程。
 */
@Service
@RequiredArgsConstructor
public class AgentService {

    private final ConversationService conversationService;
    private final AgentLoop agentLoop;
    private final AgentPlanner planner;

    public Flux<AgentStreamEvent> streamAnswer(UUID ownerUserId, AgentAskRequest request) {
        return Flux.<AgentStreamEvent>create(sink -> {
            UUID activeConversationId = request.conversationId();
            try {
                ConversationService.ConversationView conversation = resolveConversation(ownerUserId, activeConversationId, request.question());
                UUID resolvedConversationId = conversation.id();
                activeConversationId = resolvedConversationId;
                sink.next(AgentStreamEvent.start(resolvedConversationId));
                conversationService.appendUserMessage(ownerUserId, resolvedConversationId, request.question());
                List<ConversationService.MessageView> history = conversationService.recentMessages(
                        ownerUserId,
                        resolvedConversationId,
                        ConversationService.DEFAULT_HISTORY_MESSAGE_LIMIT
                );

                AgentLoop.AgentLoopResult result = agentLoop.run(
                        ownerUserId,
                        resolvedConversationId,
                        request.question(),
                        request.topK(),
                        history,
                        sink::next
                );
                String answer = streamFinalAnswer(resolvedConversationId, request.question(), history, result, sink::next);
                conversationService.appendAssistantMessage(
                        ownerUserId,
                        resolvedConversationId,
                        answer,
                        result.citations(),
                        result.metadata()
                );
                sink.next(AgentStreamEvent.done(resolvedConversationId, answer, result.citations(), result.metadata()));
                sink.complete();
            } catch (RuntimeException ex) {
                sink.next(AgentStreamEvent.error(activeConversationId, userSafeMessage(ex)));
                sink.complete();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private ConversationService.ConversationView resolveConversation(UUID ownerUserId, UUID conversationId, String question) {
        if (conversationId == null) {
            return conversationService.createConversation(ownerUserId, question);
        }
        return conversationService.requireConversation(ownerUserId, conversationId);
    }

    private String streamFinalAnswer(UUID conversationId,
                                     String question,
                                     List<ConversationService.MessageView> history,
                                     AgentLoop.AgentLoopResult result,
                                     Consumer<AgentStreamEvent> sink) {
        StringBuilder answerBuffer = new StringBuilder();
        RuntimeException streamFailure = null;
        try {
            planner.finalAnswerStream(question, history, result.steps(), result.observations())
                    .doOnNext(delta -> emitAnswerDelta(conversationId, delta, answerBuffer, sink))
                    .blockLast();
        } catch (RuntimeException ex) {
            streamFailure = ex;
        }

        if (streamFailure != null && answerBuffer.length() > 0) {
            throw streamFailure;
        }
        if (answerBuffer.length() == 0) {
            emitAnswerDelta(conversationId, fallbackFinalAnswer(question, history, result), answerBuffer, sink);
        }
        return answerBuffer.toString();
    }

    private void emitAnswerDelta(UUID conversationId,
                                 String delta,
                                 StringBuilder answerBuffer,
                                 Consumer<AgentStreamEvent> sink) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        answerBuffer.append(delta);
        sink.accept(AgentStreamEvent.delta(conversationId, delta));
    }

    private String fallbackFinalAnswer(String question,
                                       List<ConversationService.MessageView> history,
                                       AgentLoop.AgentLoopResult result) {
        String answer = planner.finalAnswer(question, history, result.steps(), result.observations());
        if ((answer == null || answer.isBlank()) && result.draftAnswer() != null && !result.draftAnswer().isBlank()) {
            answer = result.draftAnswer();
        }
        if (answer == null || answer.isBlank()) {
            return "智能体未返回回答内容";
        }
        return answer;
    }

    private String userSafeMessage(RuntimeException ex) {
        if (ex == null || ex.getMessage() == null || ex.getMessage().isBlank()) {
            return "智能体执行失败";
        }
        return ex.getMessage().trim();
    }
}