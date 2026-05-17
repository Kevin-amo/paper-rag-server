package com.lqr.paperragserver.rag.impl;

import com.lqr.paperragserver.ai.service.LlmService;
import com.lqr.paperragserver.ai.service.PromptConstructionService;
import com.lqr.paperragserver.common.constant.MetadataKeys;
import com.lqr.paperragserver.common.model.AnswerCitation;
import com.lqr.paperragserver.common.model.RagAnswer;
import com.lqr.paperragserver.common.model.RagStreamEvent;
import com.lqr.paperragserver.common.model.RetrievedChunk;
import com.lqr.paperragserver.config.RagProperties;
import com.lqr.paperragserver.conversation.service.ConversationService;
import com.lqr.paperragserver.rag.service.RagAnswerService;
import com.lqr.paperragserver.rag.service.RagRetrievalService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
        AnswerContext context = prepareAnswerContext(ownerUserId, conversationId, question, topK);
        String answer = llmService.generate(context.prompt());
        conversationService.appendAssistantMessage(ownerUserId, context.conversationId(), answer, context.citations());
        return new RagAnswer(answer, context.citations(), context.conversationId());
    }

    /**
     * 执行检索增强问答并流式返回生成过程。
     *
     * @param question 用户问题
     * @param topK 期望召回的片段数量
     * @return 问答过程事件流
     */
    @Override
    public Flux<RagStreamEvent> streamAnswer(UUID ownerUserId, UUID conversationId, String question, Integer topK) {
        return Flux.defer(() -> {
            AnswerContext context = prepareAnswerContext(ownerUserId, conversationId, question, topK);
            StringBuilder answerBuffer = new StringBuilder();
            Flux<RagStreamEvent> answerEvents = llmService.streamGenerate(context.prompt())
                    .filter(delta -> delta != null && !delta.isEmpty())
                    .doOnNext(answerBuffer::append)
                    .map(delta -> RagStreamEvent.delta(context.conversationId(), delta));
            Mono<RagStreamEvent> doneEvent = Mono.fromCallable(() -> {
                String answer = answerBuffer.toString();
                conversationService.appendAssistantMessage(ownerUserId, context.conversationId(), answer, context.citations());
                return RagStreamEvent.done(context.conversationId(), answer, context.citations());
            });
            return Flux.concat(Flux.just(RagStreamEvent.start(context.conversationId())), answerEvents, doneEvent);
        }).subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(error -> Flux.just(RagStreamEvent.error(conversationId, error.getMessage())));
    }

    private AnswerContext prepareAnswerContext(UUID ownerUserId, UUID conversationId, String question, Integer topK) {
        ConversationService.ConversationView conversation = conversationService.getOrCreateConversation(ownerUserId, conversationId, question);
        conversationService.appendUserMessage(ownerUserId, conversation.id(), question);
        List<ConversationService.MessageView> history = conversationService.recentMessages(
                ownerUserId,
                conversation.id(),
                ConversationService.DEFAULT_HISTORY_MESSAGE_LIMIT
        );

        int resolvedTopK = topK == null || topK <= 0 ? ragProperties.defaultTopK() : topK;
        String retrievalQuestion = rewriteQuestionForRetrieval(question, history);
        List<RetrievedChunk> chunks = ragRetrievalService.retrieve(ownerUserId, retrievalQuestion, resolvedTopK);
        PromptConstructionService.Prompt prompt = promptConstructionService.build(question, chunks, history);
        List<AnswerCitation> citations = buildCitations(chunks);
        return new AnswerContext(conversation.id(), prompt, citations);
    }

    private String rewriteQuestionForRetrieval(String question, List<ConversationService.MessageView> history) {
        if (history == null || history.size() <= 1) {
            return question;
        }
        String historyText = history.stream()
                .map(message -> message.role() + "：" + message.content())
                .collect(Collectors.joining("\n"));
        PromptConstructionService.Prompt rewritePrompt = new PromptConstructionService.Prompt(
                "你是一个检索查询改写器。只输出一个适合论文知识库检索的独立问题，不要解释。",
                "历史对话：\n" + historyText
                        + "\n\n当前问题：" + question
                        + "\n\n如果当前问题依赖历史上下文，请改写为语义完整的独立问题；如果不依赖历史，请原样输出当前问题。"
        );
        String rewritten = llmService.generate(rewritePrompt);
        if (rewritten == null || rewritten.isBlank()) {
            return question;
        }
        return rewritten.trim();
    }

    private List<AnswerCitation> buildCitations(List<RetrievedChunk> chunks) {
        return chunks.stream()
                .map(chunk -> new AnswerCitation(
                        chunk.chunk().sourceId(),
                        chunk.chunk().chunkId(),
                        chunk.chunk().chunkIndex(),
                        stringMetadata(chunk.chunk().metadata(), MetadataKeys.TITLE),
                        cutExcerpt(chunk.chunk().content()),
                        chunk.rankScore()))
                .toList();
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

    private record AnswerContext(
            UUID conversationId,
            PromptConstructionService.Prompt prompt,
            List<AnswerCitation> citations
    ) {
    }
}