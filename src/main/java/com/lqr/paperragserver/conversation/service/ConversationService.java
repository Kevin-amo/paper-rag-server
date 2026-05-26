package com.lqr.paperragserver.conversation.service;

import com.lqr.paperragserver.common.model.AnswerCitation;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 会话领域服务契约，定义会话创建、重命名、消息追加、历史读取和软删除能力。
 */
public interface ConversationService {

    int DEFAULT_HISTORY_MESSAGE_LIMIT = 12;

    ConversationView createConversation(UUID ownerUserId, String title);

    ConversationView renameConversation(UUID ownerUserId, UUID conversationId, String title);

    ConversationView requireConversation(UUID ownerUserId, UUID conversationId);

    ConversationView getOrCreateConversation(UUID ownerUserId, UUID conversationId, String firstQuestion);

    List<ConversationView> listConversations(UUID ownerUserId);

    List<MessageView> listMessages(UUID ownerUserId, UUID conversationId);

    List<MessageView> recentMessages(UUID ownerUserId, UUID conversationId, int limit);

    MessageView appendUserMessage(UUID ownerUserId, UUID conversationId, String content);

    MessageView appendAssistantMessage(UUID ownerUserId, UUID conversationId, String content, List<AnswerCitation> citations);

    MessageView appendAssistantMessage(UUID ownerUserId, UUID conversationId, String content, List<AnswerCitation> citations, Object metadata);

    void deleteConversation(UUID ownerUserId, UUID conversationId);

    /**
     * 会话列表和详情使用的只读视图，承载会话归属、标题和时间信息。
     */
    record ConversationView(
            UUID id,
            UUID ownerUserId,
            String title,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    /**
     * 会话消息只读视图，承载消息角色、顺序、内容、引用和扩展元数据。
     */
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