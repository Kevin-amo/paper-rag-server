package com.lqr.paperragserver.agent.application;

import com.lqr.paperragserver.agent.dto.AgentAskRequest;
import com.lqr.paperragserver.agent.dto.AgentStreamEvent;
import com.lqr.paperragserver.agent.planning.AgentPlanner;
import com.lqr.paperragserver.agent.service.AgentLoop;
import com.lqr.paperragserver.common.logging.LogSanitizer;
import com.lqr.paperragserver.conversation.service.ConversationService;
import com.lqr.paperragserver.literature.model.LiteratureSearchContext;
import com.lqr.paperragserver.literature.support.LiteratureSearchContextResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentChatService {

    private final ConversationService conversationService;
    private final AgentLoop agentLoop;
    private final AgentPlanner planner;
    private final LiteratureSearchContextResolver literatureSearchContextResolver;

    public Flux<AgentStreamEvent> streamAnswer(UUID ownerUserId, AgentAskRequest request) {
        return Flux.<AgentStreamEvent>create(sink -> {
            UUID activeConversationId = request.conversationId();
            long startNanos = System.nanoTime();
            log.info("agent.ask.start ownerUserId={} conversationId={} questionLength={} questionExcerpt={} topK={}",
                    ownerUserId, activeConversationId, textLength(request.question()), LogSanitizer.safeExcerpt(request.question(), 160), request.topK());
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
                LiteratureSearchContext lastLiteratureContext = literatureSearchContextResolver.resolve(history).orElse(null);
                log.info("agent.ask.context ownerUserId={} conversationId={} historyCount={} topK={} hasLiteratureContext={}",
                        ownerUserId, resolvedConversationId, history.size(), request.topK(), lastLiteratureContext != null);

                AgentLoop.AgentLoopResult result = agentLoop.run(
                        ownerUserId,
                        resolvedConversationId,
                        request.question(),
                        request.topK(),
                        history,
                        lastLiteratureContext,
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
                log.info("agent.ask.done ownerUserId={} conversationId={} answerLength={} citationCount={} stepCount={} costMs={}",
                        ownerUserId, resolvedConversationId, textLength(answer), result.citations().size(), result.steps().size(), elapsedMs(startNanos));
                sink.next(AgentStreamEvent.done(resolvedConversationId, answer, result.citations(), result.metadata()));
                sink.complete();
            } catch (RuntimeException ex) {
                log.error("agent.ask.failed ownerUserId={} conversationId={} questionLength={} costMs={}",
                        ownerUserId, activeConversationId, textLength(request.question()), elapsedMs(startNanos), ex);
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
        long startNanos = System.nanoTime();
        log.info("agent.answer.start conversationId={} stepCount={} observationCount={} citationCount={}",
                conversationId, result.steps().size(), result.observations().size(), result.citations().size());
        StringBuilder answerBuffer = new StringBuilder();
        RuntimeException streamFailure = null;
        try {
            planner.finalAnswerStream(question, history, result.steps(), result.observations())
                    .doOnNext(delta -> emitAnswerDelta(conversationId, delta, answerBuffer, sink))
                    .blockLast();
        } catch (RuntimeException ex) {
            streamFailure = ex;
            log.warn("agent.answer.failed conversationId={} partialAnswerLength={} costMs={}",
                    conversationId, answerBuffer.length(), elapsedMs(startNanos), ex);
        }

        if (streamFailure != null && answerBuffer.length() > 0) {
            throw streamFailure;
        }
        if (answerBuffer.length() == 0) {
            log.warn("agent.answer.fallback conversationId={} reason=EMPTY_STREAM", conversationId);
            emitAnswerDelta(conversationId, fallbackFinalAnswer(question, history, result), answerBuffer, sink);
        }
        log.info("agent.answer.done conversationId={} answerLength={} costMs={}", conversationId, answerBuffer.length(), elapsedMs(startNanos));
        log.debug("agent.answer.debug conversationId={} answerExcerpt={}", conversationId, LogSanitizer.safeExcerpt(answerBuffer.toString(), 500));
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

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private int textLength(String text) {
        return text == null ? 0 : text.length();
    }

    private String userSafeMessage(RuntimeException ex) {
        if (ex == null || ex.getMessage() == null || ex.getMessage().isBlank()) {
            return "智能体执行失败";
        }
        return ex.getMessage().trim();
    }
}