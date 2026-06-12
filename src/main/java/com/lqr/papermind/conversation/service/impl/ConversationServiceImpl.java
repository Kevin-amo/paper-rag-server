package com.lqr.papermind.conversation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.papermind.common.model.AnswerCitation;
import com.lqr.papermind.conversation.entity.Conversation;
import com.lqr.papermind.conversation.entity.ConversationMessage;
import com.lqr.papermind.conversation.mapper.ConversationMapper;
import com.lqr.papermind.conversation.mapper.ConversationMessageMapper;
import com.lqr.papermind.conversation.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 会话服务实现，负责会话生命周期、消息读写、历史窗口和回答引用元数据的持久化转换。
 */
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    /** 会话标题最大长度 */
    private static final int MAX_TITLE_LENGTH = 60;
    /** 历史消息查询最大窗口大小 */
    private static final int MAX_HISTORY_LIMIT = 40;
    /** 引用列表反序列化类型 */
    private static final TypeReference<List<AnswerCitation>> CITATION_LIST_TYPE = new TypeReference<>() {
    };
    /** 元数据反序列化类型 */
    private static final TypeReference<Map<String, Object>> METADATA_MAP_TYPE = new TypeReference<>() {
    };

    /** 会话数据访问接口 */
    private final ConversationMapper conversationMapper;
    /** 会话消息数据访问接口 */
    private final ConversationMessageMapper messageMapper;
    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper;

    /**
     * 为指定用户创建会话，并对标题做默认值和长度归一化。
     *
     * @param ownerUserId 会话所属用户 ID
     * @param title 原始会话标题
     * @return 创建后的会话视图
     */
    @Override
    public ConversationView createConversation(UUID ownerUserId, String title) {
        Conversation entity = new Conversation();
        OffsetDateTime now = OffsetDateTime.now();
        entity.setId(UUID.randomUUID());
        entity.setOwnerUserId(ownerUserId);
        entity.setTitle(normalizeTitle(title));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        conversationMapper.insert(entity);
        return toConversationView(entity);
    }

    /**
     * 修改指定用户会话标题，并返回最新会话状态。
     *
     * @param ownerUserId 会话所属用户 ID
     * @param conversationId 会话 ID
     * @param title 新标题
     * @return 修改后的会话视图
     */
    @Override
    @Transactional
    public ConversationView renameConversation(UUID ownerUserId, UUID conversationId, String title) {
        requireConversation(ownerUserId, conversationId);
        conversationMapper.updateTitle(conversationId, ownerUserId, normalizeTitle(title));
        return requireConversation(ownerUserId, conversationId);
    }

    /**
     * 校验会话 ID 是否有效，以及会话是否属于当前用户且未删除。
     *
     * @param ownerUserId 会话所属用户 ID
     * @param conversationId 会话 ID
     * @return 命中的会话视图
     */
    @Override
    public ConversationView requireConversation(UUID ownerUserId, UUID conversationId) {
        if (conversationId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "会话 ID 不能为空");
        }
        Conversation entity = conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getId, conversationId)
                .eq(Conversation::getOwnerUserId, ownerUserId)
                .isNull(Conversation::getDeletedAt));
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在或无权访问");
        }
        return toConversationView(entity);
    }

    /**
     * 复用已有会话；未传会话 ID 时使用首个问题创建新会话。
     *
     * @param ownerUserId 会话所属用户 ID
     * @param conversationId 可选会话 ID
     * @param firstQuestion 首个问题内容
     * @return 已存在或新创建的会话视图
     */
    @Override
    @Transactional
    public ConversationView getOrCreateConversation(UUID ownerUserId, UUID conversationId, String firstQuestion) {
        if (conversationId != null) {
            return requireConversation(ownerUserId, conversationId);
        }
        return createConversation(ownerUserId, firstQuestion);
    }

    /**
     * 查询指定用户未删除的会话列表。
     *
     * @param ownerUserId 会话所属用户 ID
     * @return 按更新时间倒序排列的会话视图列表
     */
    @Override
    public List<ConversationView> listConversations(UUID ownerUserId) {
        return conversationMapper.selectList(new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getOwnerUserId, ownerUserId)
                        .isNull(Conversation::getDeletedAt)
                        .orderByDesc(Conversation::getUpdatedAt))
                .stream()
                .map(this::toConversationView)
                .toList();
    }

    /**
     * 查询指定会话的完整消息列表。
     *
     * @param ownerUserId 会话所属用户 ID
     * @param conversationId 会话 ID
     * @return 按消息顺序升序排列的消息视图列表
     */
    @Override
    public List<MessageView> listMessages(UUID ownerUserId, UUID conversationId) {
        requireConversation(ownerUserId, conversationId);
        return messageMapper.selectList(new LambdaQueryWrapper<ConversationMessage>()
                        .eq(ConversationMessage::getConversationId, conversationId)
                        .eq(ConversationMessage::getOwnerUserId, ownerUserId)
                        .orderByAsc(ConversationMessage::getMessageOrder))
                .stream()
                .map(this::toMessageView)
                .toList();
    }

    /**
     * 查询指定会话最近的消息窗口，并限制最大窗口大小。
     *
     * @param ownerUserId 会话所属用户 ID
     * @param conversationId 会话 ID
     * @param limit 期望返回数量
     * @return 最近消息视图列表
     */
    @Override
    public List<MessageView> recentMessages(UUID ownerUserId, UUID conversationId, int limit) {
        requireConversation(ownerUserId, conversationId);
        int safeLimit = Math.max(1, Math.min(limit, MAX_HISTORY_LIMIT));
        return messageMapper.selectRecent(conversationId, ownerUserId, safeLimit).stream()
                .map(this::toMessageView)
                .toList();
    }

    /**
     * 追加用户消息，并刷新会话活跃时间。
     *
     * @param ownerUserId 会话所属用户 ID
     * @param conversationId 会话 ID
     * @param content 用户消息内容
     * @return 持久化后的消息视图
     */
    @Override
    @Transactional
    public MessageView appendUserMessage(UUID ownerUserId, UUID conversationId, String content) {
        MessageView message = appendMessage(ownerUserId, conversationId, "USER", content, null, null);
        conversationMapper.touch(conversationId, ownerUserId);
        return message;
    }

    /**
     * 追加助手消息，并使用空元数据。
     *
     * @param ownerUserId 会话所属用户 ID
     * @param conversationId 会话 ID
     * @param content 助手消息内容
     * @param citations 回答引用来源
     * @return 持久化后的消息视图
     */
    @Override
    @Transactional
    public MessageView appendAssistantMessage(UUID ownerUserId, UUID conversationId, String content, List<AnswerCitation> citations) {
        return appendAssistantMessage(ownerUserId, conversationId, content, citations, null);
    }

    /**
     * 追加助手消息、回答引用和扩展元数据，并刷新会话活跃时间。
     *
     * @param ownerUserId 会话所属用户 ID
     * @param conversationId 会话 ID
     * @param content 助手消息内容
     * @param citations 回答引用来源
     * @param metadata 扩展元数据
     * @return 持久化后的消息视图
     */
    @Override
    @Transactional
    public MessageView appendAssistantMessage(UUID ownerUserId, UUID conversationId, String content, List<AnswerCitation> citations, Object metadata) {
        MessageView message = appendMessage(ownerUserId, conversationId, "ASSISTANT", content, citations, metadata);
        conversationMapper.touch(conversationId, ownerUserId);
        return message;
    }

    /**
     * 软删除指定用户的会话。
     *
     * @param ownerUserId 会话所属用户 ID
     * @param conversationId 会话 ID
     */
    @Override
    public void deleteConversation(UUID ownerUserId, UUID conversationId) {
        requireConversation(ownerUserId, conversationId);
        conversationMapper.softDelete(conversationId, ownerUserId);
    }

    /**
     * 统一追加一条会话消息，并分配消息顺序号。
     *
     * @param ownerUserId 会话所属用户 ID
     * @param conversationId 会话 ID
     * @param role 消息角色
     * @param content 消息内容
     * @param citations 回答引用来源
     * @param metadata 扩展元数据
     * @return 持久化后的消息视图
     */
    private MessageView appendMessage(UUID ownerUserId, UUID conversationId, String role, String content, List<AnswerCitation> citations, Object metadata) {
        requireConversation(ownerUserId, conversationId);
        String normalizedContent = requireContent(content);
        ConversationMessage entity = new ConversationMessage();
        entity.setId(UUID.randomUUID());
        entity.setConversationId(conversationId);
        entity.setOwnerUserId(ownerUserId);
        entity.setRole(role);
        entity.setMessageOrder(messageMapper.nextMessageOrder(conversationId, ownerUserId));
        entity.setContent(normalizedContent);
        entity.setCitations(citations == null || citations.isEmpty() ? null : citations);
        entity.setMetadata(metadata);
        entity.setCreatedAt(OffsetDateTime.now());
        messageMapper.insert(entity);
        return toMessageView(entity);
    }

    /**
     * 归一化会话标题，处理空标题、连续空白和最大长度。
     *
     * @param title 原始标题
     * @return 可持久化的会话标题
     */
    private String normalizeTitle(String title) {
        String normalized = title == null || title.isBlank() ? "新会话" : title.trim().replaceAll("\\s+", " ");
        return normalized.length() <= MAX_TITLE_LENGTH ? normalized : normalized.substring(0, MAX_TITLE_LENGTH);
    }

    /**
     * 校验并归一化消息内容。
     *
     * @param content 原始消息内容
     * @return 去除首尾空白后的消息内容
     */
    private String requireContent(String content) {
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "消息内容不能为空");
        }
        return content.trim();
    }

    /**
     * 将会话实体转换为对外只读视图。
     *
     * @param entity 会话实体
     * @return 会话视图
     */
    private ConversationView toConversationView(Conversation entity) {
        return new ConversationView(
                entity.getId(),
                entity.getOwnerUserId(),
                entity.getTitle(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    /**
     * 将消息实体转换为对外只读视图。
     *
     * @param entity 消息实体
     * @return 消息视图
     */
    private MessageView toMessageView(ConversationMessage entity) {
        return new MessageView(
                entity.getId(),
                entity.getConversationId(),
                entity.getRole(),
                entity.getMessageOrder(),
                entity.getContent(),
                parseCitations(entity.getCitations()),
                parseMetadata(entity.getMetadata()),
                entity.getCreatedAt()
        );
    }

    /**
     * 将数据库中的引用 JSON 对象转换为回答引用列表。
     *
     * @param value 数据库读取出的引用对象
     * @return 回答引用列表
     */
    private List<AnswerCitation> parseCitations(Object value) {
        if (value == null) {
            return List.of();
        }
        return objectMapper.convertValue(value, CITATION_LIST_TYPE);
    }

    /**
     * 将数据库中的元数据 JSON 对象转换为键值结构。
     *
     * @param value 数据库读取出的元数据对象
     * @return 元数据键值结构
     */
    private Map<String, Object> parseMetadata(Object value) {
        if (value == null) {
            return null;
        }
        return objectMapper.convertValue(value, METADATA_MAP_TYPE);
    }

}