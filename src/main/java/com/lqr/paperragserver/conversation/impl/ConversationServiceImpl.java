package com.lqr.paperragserver.conversation.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.paperragserver.common.model.AnswerCitation;
import com.lqr.paperragserver.conversation.entity.Conversation;
import com.lqr.paperragserver.conversation.entity.ConversationMessage;
import com.lqr.paperragserver.conversation.mapper.ConversationMapper;
import com.lqr.paperragserver.conversation.mapper.ConversationMessageMapper;
import com.lqr.paperragserver.conversation.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private static final int MAX_TITLE_LENGTH = 60;
    private static final int MAX_HISTORY_LIMIT = 40;
    private static final TypeReference<List<AnswerCitation>> CITATION_LIST_TYPE = new TypeReference<>() {
    };

    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper messageMapper;
    private final ObjectMapper objectMapper;

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

    @Override
    @Transactional
    public ConversationView getOrCreateConversation(UUID ownerUserId, UUID conversationId, String firstQuestion) {
        if (conversationId != null) {
            return requireConversation(ownerUserId, conversationId);
        }
        return createConversation(ownerUserId, firstQuestion);
    }

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

    @Override
    public List<MessageView> recentMessages(UUID ownerUserId, UUID conversationId, int limit) {
        requireConversation(ownerUserId, conversationId);
        int safeLimit = Math.max(1, Math.min(limit, MAX_HISTORY_LIMIT));
        return messageMapper.selectRecent(conversationId, ownerUserId, safeLimit).stream()
                .map(this::toMessageView)
                .toList();
    }

    @Override
    @Transactional
    public MessageView appendUserMessage(UUID ownerUserId, UUID conversationId, String content) {
        MessageView message = appendMessage(ownerUserId, conversationId, "USER", content, null);
        conversationMapper.touch(conversationId, ownerUserId);
        return message;
    }

    @Override
    @Transactional
    public MessageView appendAssistantMessage(UUID ownerUserId, UUID conversationId, String content, List<AnswerCitation> citations) {
        MessageView message = appendMessage(ownerUserId, conversationId, "ASSISTANT", content, citations);
        conversationMapper.touch(conversationId, ownerUserId);
        return message;
    }

    @Override
    public void deleteConversation(UUID ownerUserId, UUID conversationId) {
        requireConversation(ownerUserId, conversationId);
        conversationMapper.softDelete(conversationId, ownerUserId);
    }

    private MessageView appendMessage(UUID ownerUserId, UUID conversationId, String role, String content, List<AnswerCitation> citations) {
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
        entity.setCreatedAt(OffsetDateTime.now());
        messageMapper.insert(entity);
        return toMessageView(entity);
    }

    private String normalizeTitle(String title) {
        String normalized = title == null || title.isBlank() ? "新会话" : title.trim().replaceAll("\\s+", " ");
        return normalized.length() <= MAX_TITLE_LENGTH ? normalized : normalized.substring(0, MAX_TITLE_LENGTH);
    }

    private String requireContent(String content) {
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "消息内容不能为空");
        }
        return content.trim();
    }

    private ConversationView toConversationView(Conversation entity) {
        return new ConversationView(entity.getId(), entity.getOwnerUserId(), entity.getTitle(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private MessageView toMessageView(ConversationMessage entity) {
        return new MessageView(
                entity.getId(),
                entity.getConversationId(),
                entity.getRole(),
                entity.getMessageOrder(),
                entity.getContent(),
                parseCitations(entity.getCitations()),
                entity.getCreatedAt()
        );
    }

    private List<AnswerCitation> parseCitations(Object value) {
        if (value == null) {
            return List.of();
        }
        return objectMapper.convertValue(value, CITATION_LIST_TYPE);
    }
}