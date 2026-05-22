package com.lqr.paperragserver.conversation.service;

import com.lqr.paperragserver.common.model.AnswerCitation;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ConversationService {

    int DEFAULT_HISTORY_MESSAGE_LIMIT = 12;

    ConversationView createConversation(UUID ownerUserId, String title);

    ConversationView createConversation(UUID ownerUserId, String title, String type);

    ConversationView renameConversation(UUID ownerUserId, UUID conversationId, String title);

    ConversationView requireConversation(UUID ownerUserId, UUID conversationId);

    ConversationView getOrCreateConversation(UUID ownerUserId, UUID conversationId, String firstQuestion);

    ConversationView getOrCreateConversation(UUID ownerUserId, UUID conversationId, String firstQuestion, String type);

    List<ConversationView> listConversations(UUID ownerUserId);

    List<MessageView> listMessages(UUID ownerUserId, UUID conversationId);

    List<MessageView> recentMessages(UUID ownerUserId, UUID conversationId, int limit);

    MessageView appendUserMessage(UUID ownerUserId, UUID conversationId, String content);

    MessageView appendAssistantMessage(UUID ownerUserId, UUID conversationId, String content, List<AnswerCitation> citations);

    MessageView appendAssistantMessage(UUID ownerUserId, UUID conversationId, String content, List<AnswerCitation> citations, Object metadata);

    void deleteConversation(UUID ownerUserId, UUID conversationId);

    record ConversationView(
            UUID id,
            UUID ownerUserId,
            String title,
            String type,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    record MessageView(
            UUID id,
            UUID conversationId,
            String role,
            int messageOrder,
            String content,
            List<AnswerCitation> citations,
            Map<String, Object> metadata,
            OffsetDateTime createdAt
    ) {
    }
}