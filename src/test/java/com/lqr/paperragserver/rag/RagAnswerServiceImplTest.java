package com.lqr.paperragserver.rag;

import com.lqr.paperragserver.ai.service.LlmService;
import com.lqr.paperragserver.ai.service.PromptConstructionService;
import com.lqr.paperragserver.common.model.AnswerCitation;
import com.lqr.paperragserver.common.model.DocumentChunk;
import com.lqr.paperragserver.common.model.RagAnswer;
import com.lqr.paperragserver.common.model.RetrievedChunk;
import com.lqr.paperragserver.config.RagProperties;
import com.lqr.paperragserver.conversation.service.ConversationService;
import com.lqr.paperragserver.rag.impl.RagAnswerServiceImpl;
import com.lqr.paperragserver.rag.service.RagRetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DefaultRagAnswerService 的单元测试类。
 * 主要验证 RAG 回答服务的以下核心行为：
 * 能否正确协调检索、提示构造和 LLM 生成三大组件；
 * 能否将检索到的文档块元数据完整封装为引用信息（Citation）；
 * 能否正确处理调用方显式传入的 topK 参数，优先使用它而非默认配置。
 * 所有依赖均通过 Mockito 进行模拟，实现快速、稳定的单元测试。
 */
class RagAnswerServiceImplTest {

    // 模拟的检索服务
    private final RagRetrievalService ragRetrievalService = mock(RagRetrievalService.class);
    // 模拟的提示词构造服务
    private final PromptConstructionService promptConstructionService = mock(PromptConstructionService.class);
    // 模拟的 LLM 服务
    private final LlmService llmService = mock(LlmService.class);
    // 模拟的会话服务
    private final ConversationService conversationService = mock(ConversationService.class);
    // 测试用的配置：最大上下文800，单个块最大长度120，默认topK=5，重叠度0
    private final RagProperties ragProperties = new RagProperties(800, 120, 5, 0);
    private final UUID ownerUserId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    // 被测试的服务实例
    private RagAnswerServiceImpl service;

    /**
     * 每个测试方法执行前，用模拟对象重新创建服务实例，保证测试独立性。
     */
    @BeforeEach
    void setUp() {
        service = new RagAnswerServiceImpl(ragRetrievalService, promptConstructionService, llmService, ragProperties, conversationService);
        when(conversationService.getOrCreateConversation(eq(ownerUserId), any(), anyString()))
                .thenReturn(new ConversationService.ConversationView(conversationId, ownerUserId, "测试会话", null, null));
        when(conversationService.recentMessages(eq(ownerUserId), eq(conversationId), anyInt()))
                .thenReturn(List.of());
    }

    /**
     * 测试用例：回答应当返回包含完整来源信息的引用。
     * <p>
     * 验证点：
     * <ol>
     *   <li>检索服务被调用，且使用默认 topK 值（5）；</li>
     *   <li>LLM 返回的文本直接成为答案的主体；</li>
     *   <li>每个检索到的文档块都被转换成一个 AnswerCitation，其中包含：
     *        sourceId、chunkId、chunkIndex、title、excerpt 和 score。</li>
     * </ol>
     */
    @Test
    void answerShouldReturnCitationsWithSourceAndChunkInfo() {
        // --- 准备测试数据 ---
        // 构造一个模拟的文档块
        DocumentChunk chunk = new DocumentChunk(
                "chunk-1",                          // 块ID
                "source-1",                         // 源ID
                4,                                  // 块索引
                "This is the relevant text of the paper.",   // 块文本
                Map.of("title", "Paper A")          // 元数据（标题）
        );
        // 包装为带分数的检索结果
        RetrievedChunk retrievedChunk = new RetrievedChunk(chunk, 0.93);

        // --- 设置模拟行为 ---
        // 当检索服务收到指定问题和 topK=5 时，返回上述检索结果
        when(ragRetrievalService.retrieve(ownerUserId, "what is the main idea", 5))
                .thenReturn(List.of(retrievedChunk));
        // 提示构造服务任意调用都返回一个简单的 Prompt 对象
        when(promptConstructionService.build(anyString(), anyList(), anyList()))
                .thenReturn(new PromptConstructionService.Prompt("sys", "user"));
        // LLM 服务任意调用都返回固定答案
        when(llmService.generate(any())).thenReturn("final answer");

        // --- 执行被测方法 ---
        RagAnswer answer = service.answer(ownerUserId, null, "what is the main idea", null);

        // --- 断言验证 ---
        // 答案文本应与 LLM 返回的一致
        assertThat(answer.answer()).isEqualTo("final answer");
        // 应当产生一个引用
        assertThat(answer.citations()).hasSize(1);
        AnswerCitation citation = answer.citations().get(0);
        // 引用的各个字段应与检索到的文档块严格对应
        assertThat(citation.sourceId()).isEqualTo("source-1");
        assertThat(citation.chunkId()).isEqualTo("chunk-1");
        assertThat(citation.chunkIndex()).isEqualTo(4);
        assertThat(citation.title()).isEqualTo("Paper A");
        assertThat(citation.excerpt()).isEqualTo("This is the relevant text of the paper.");
        assertThat(citation.rankScore()).isEqualTo(0.93);
        // 确认检索服务确实被调用过一次，且参数正确
        verify(ragRetrievalService).retrieve(ownerUserId, "what is the main idea", 5);
    }

    /**
     * 测试用例：当调用方显式传入 topK 时，服务应使用该值而非默认值。
     * <p>
     * 验证点：
     * <ol>
     *   <li>传入 topK=2 后，检索服务收到的 topK 参数也应为 2；</li>
     *   <li>即使检索结果为空，流程也应正常完成并返回答案。</li>
     * </ol>
     */
    @Test
    void answerShouldRespectExplicitTopK() {
        // 设置检索服务返回空列表（仅关注 topK 传递）
        when(ragRetrievalService.retrieve(ownerUserId, "question", 2)).thenReturn(List.of());
        when(promptConstructionService.build(anyString(), anyList(), anyList()))
                .thenReturn(new PromptConstructionService.Prompt("sys", "user"));
        when(llmService.generate(any())).thenReturn("answer");

        // 执行，显式传入 topK=2
        service.answer(ownerUserId, null, "question", 2);

        // 验证检索服务确实被调用，且 topK 为 2
        verify(ragRetrievalService).retrieve(ownerUserId, "question", 2);
    }
}