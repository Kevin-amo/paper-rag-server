package com.lqr.paperragserver.rag.impl;

import com.lqr.paperragserver.ai.service.LlmService;
import com.lqr.paperragserver.ai.service.PromptConstructionService;
import com.lqr.paperragserver.common.model.AnswerCitation;
import com.lqr.paperragserver.common.model.RagAnswer;
import com.lqr.paperragserver.common.model.RetrievedChunk;
import com.lqr.paperragserver.config.RagProperties;
import com.lqr.paperragserver.common.constant.MetadataKeys;
import com.lqr.paperragserver.conversation.service.ConversationService;
import com.lqr.paperragserver.rag.service.RagAnswerService;
import com.lqr.paperragserver.rag.service.RagRetrievalService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 默认问答编排实现。
 */
@Service
public class RagAnswerServiceImpl implements RagAnswerService {

    private final RagRetrievalService ragRetrievalService;
    private final PromptConstructionService promptConstructionService;
    private final LlmService llmService;
    private final RagProperties ragProperties;
    private final ConversationService conversationService;

    /**
     * 创建问答编排服务并注入所需依赖。
     *
     * @param ragRetrievalService 检索服务
     * @param promptConstructionService 提示词构造服务
     * @param llmService 大模型调用服务
     * @param ragProperties RAG 配置
     */
    public RagAnswerServiceImpl(RagRetrievalService ragRetrievalService,
                                PromptConstructionService promptConstructionService,
                                LlmService llmService,
                                RagProperties ragProperties,
                                ConversationService conversationService) {
        this.ragRetrievalService = ragRetrievalService;
        this.promptConstructionService = promptConstructionService;
        this.llmService = llmService;
        this.ragProperties = ragProperties;
        this.conversationService = conversationService;
    }

    /**
     * 执行检索增强问答并返回回答及引用片段。
     *
     * @param question 用户问题
     * @param topK 期望召回的片段数量
     * @return 包含回答正文和引用信息的结果对象
     */
    @Override
    public RagAnswer answer(UUID ownerUserId, UUID conversationId, String question, Integer topK) {
        ConversationService.ConversationView conversation = conversationService.getOrCreateConversation(ownerUserId, conversationId, question);
        conversationService.appendUserMessage(ownerUserId, conversation.id(), question);

        int resolvedTopK = topK == null || topK <= 0 ? ragProperties.defaultTopK() : topK;
        List<RetrievedChunk> chunks = ragRetrievalService.retrieve(ownerUserId, question, resolvedTopK);
        List<ConversationService.MessageView> history = conversationService.recentMessages(
                ownerUserId,
                conversation.id(),
                ConversationService.DEFAULT_HISTORY_MESSAGE_LIMIT
        );
        PromptConstructionService.Prompt prompt = promptConstructionService.build(question, chunks, history);
        String answer = llmService.generate(prompt);
        List<AnswerCitation> citations = chunks.stream()
                .map(chunk -> new AnswerCitation(
                        chunk.chunk().sourceId(),
                        chunk.chunk().chunkId(),
                        chunk.chunk().chunkIndex(),
                        stringMetadata(chunk.chunk().metadata(), MetadataKeys.TITLE),
                        cutExcerpt(chunk.chunk().content()),
                        chunk.rankScore()))
                .toList();
        conversationService.appendAssistantMessage(ownerUserId, conversation.id(), answer, citations);
        return new RagAnswer(answer, citations, conversation.id());
    }

    /**
     * 截断引用摘要，避免返回过长正文片段。
     *
     * @param content 原始片段内容
     * @return 截断后的摘要文本
     */
    private String cutExcerpt(String content) {
        if (content == null) {
            return "";
        }
        int maxLength = 160;
        return content.length() <= maxLength ? content : content.substring(0, maxLength) + "...";
    }

    /**
     * 从元数据中读取字符串字段。
     *
     * @param metadata 元数据映射
     * @param key 字段名
     * @return 对应的字符串值，不存在时返回 null
     */
    private String stringMetadata(Map<String, Object> metadata, String key) {
        if (metadata == null || !metadata.containsKey(key)) {
            return null;
        }
        Object value = metadata.get(key);
        return value == null ? null : String.valueOf(value);
    }
}