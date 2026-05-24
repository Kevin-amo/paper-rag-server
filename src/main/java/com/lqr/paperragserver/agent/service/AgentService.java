package com.lqr.paperragserver.agent.service;

import com.lqr.paperragserver.agent.model.AgentAskRequest;
import com.lqr.paperragserver.agent.model.AgentStreamEvent;
import com.lqr.paperragserver.conversation.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentService {

    private static final String CONVERSATION_TYPE_RAG_COMPATIBLE = "RAG";

    private final ConversationService conversationService;
    private final AgentLoop agentLoop;

    public Flux<AgentStreamEvent> streamAnswer(UUID ownerUserId, AgentAskRequest request) {
        return Flux.<AgentStreamEvent>create(sink -> {
            UUID conversationId = request.conversationId();
            try {
                ConversationService.ConversationView conversation = resolveConversation(ownerUserId, conversationId, request.question());
                UUID resolvedConversationId = conversation.id();
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
                conversationService.appendAssistantMessage(
                        ownerUserId,
                        resolvedConversationId,
                        result.answer(),
                        result.citations(),
                        result.metadata()
                );
                emitAnswerDeltas(resolvedConversationId, result.answer(), sink::next);
                sink.next(AgentStreamEvent.done(resolvedConversationId, result.answer(), result.citations(), result.metadata()));
                sink.complete();
            } catch (RuntimeException ex) {
                sink.next(AgentStreamEvent.error(conversationId, ex.getMessage()));
                sink.complete();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private ConversationService.ConversationView resolveConversation(UUID ownerUserId, UUID conversationId, String question) {
        if (conversationId == null) {
            return conversationService.createConversation(ownerUserId, question, CONVERSATION_TYPE_RAG_COMPATIBLE);
        }
        return conversationService.requireConversation(ownerUserId, conversationId);
    }

    private void emitAnswerDeltas(UUID conversationId, String answer, java.util.function.Consumer<AgentStreamEvent> sink) {
        if (answer == null || answer.isBlank()) {
            return;
        }
        int chunkSize = 24;
        for (int start = 0; start < answer.length(); start += chunkSize) {
            int end = Math.min(answer.length(), start + chunkSize);
            sink.accept(AgentStreamEvent.delta(conversationId, answer.substring(start, end)));
        }
    }
}