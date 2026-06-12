package com.lqr.papermind.conversation.service;

import com.lqr.papermind.common.model.AnswerCitation;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 会话领域服务契约，定义会话创建、重命名、消息追加、历史读取和软删除能力。
 */
public interface ConversationService {

    /** 默认历史消息查询上限 */
    int DEFAULT_HISTORY_MESSAGE_LIMIT = 20;

    /**
     * 为指定用户创建一个新的会话。
     *
     * @param ownerUserId 会话所属用户 ID
     * @param title 会话标题，为空时使用默认标题
     * @return 创建后的会话视图
     */
    ConversationView createConversation(UUID ownerUserId, String title);

    /**
     * 修改指定用户名下会话的标题。
     *
     * @param ownerUserId 会话所属用户 ID
     * @param conversationId 会话 ID
     * @param title 新标题，为空时使用默认标题
     * @return 修改后的会话视图
     */
    ConversationView renameConversation(UUID ownerUserId, UUID conversationId, String title);

    /**
     * 校验会话是否存在且属于指定用户。
     *
     * @param ownerUserId 会话所属用户 ID
     * @param conversationId 会话 ID
     * @return 命中的会话视图
     */
    ConversationView requireConversation(UUID ownerUserId, UUID conversationId);

    /**
     * 获取已有会话；当会话 ID 为空时，根据首个问题创建新会话。
     *
     * @param ownerUserId 会话所属用户 ID
     * @param conversationId 可选会话 ID
     * @param firstQuestion 首个问题，用于生成默认会话标题
     * @return 已存在或新创建的会话视图
     */
    ConversationView getOrCreateConversation(UUID ownerUserId, UUID conversationId, String firstQuestion);

    /**
     * 查询指定用户的会话列表。
     *
     * @param ownerUserId 会话所属用户 ID
     * @return 按活跃时间倒序排列的会话视图列表
     */
    List<ConversationView> listConversations(UUID ownerUserId);

    /**
     * 查询指定会话的全部消息。
     *
     * @param ownerUserId 会话所属用户 ID
     * @param conversationId 会话 ID
     * @return 按消息顺序升序排列的消息视图列表
     */
    List<MessageView> listMessages(UUID ownerUserId, UUID conversationId);

    /**
     * 查询指定会话最近的消息窗口。
     *
     * @param ownerUserId 会话所属用户 ID
     * @param conversationId 会话 ID
     * @param limit 期望返回的消息数量
     * @return 最近消息视图列表
     */
    List<MessageView> recentMessages(UUID ownerUserId, UUID conversationId, int limit);

    /**
     * 向指定会话追加用户消息。
     *
     * @param ownerUserId 会话所属用户 ID
     * @param conversationId 会话 ID
     * @param content 用户消息内容
     * @return 持久化后的消息视图
     */
    MessageView appendUserMessage(UUID ownerUserId, UUID conversationId, String content);

    /**
     * 向指定会话追加助手消息。
     *
     * @param ownerUserId 会话所属用户 ID
     * @param conversationId 会话 ID
     * @param content 助手消息内容
     * @param citations 回答引用来源
     * @return 持久化后的消息视图
     */
    MessageView appendAssistantMessage(UUID ownerUserId, UUID conversationId, String content, List<AnswerCitation> citations);

    /**
     * 向指定会话追加带扩展元数据的助手消息。
     *
     * @param ownerUserId 会话所属用户 ID
     * @param conversationId 会话 ID
     * @param content 助手消息内容
     * @param citations 回答引用来源
     * @param metadata 扩展元数据
     * @return 持久化后的消息视图
     */
    MessageView appendAssistantMessage(UUID ownerUserId, UUID conversationId, String content, List<AnswerCitation> citations, Object metadata);

    /**
     * 软删除指定用户名下的会话。
     *
     * @param ownerUserId 会话所属用户 ID
     * @param conversationId 会话 ID
     */
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