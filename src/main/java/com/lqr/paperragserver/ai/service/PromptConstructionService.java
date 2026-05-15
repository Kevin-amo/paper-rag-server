package com.lqr.paperragserver.ai.service;

import com.lqr.paperragserver.common.model.RetrievedChunk;
import com.lqr.paperragserver.conversation.service.ConversationService;

import java.util.List;

/**
 * 提示词构造服务接口。
 *
 * <p>实现类负责把用户问题和检索到的上下文整理成适合大模型使用的系统提示词与用户提示词。</p>
 */
public interface PromptConstructionService {

    /**
     * 根据问题和上下文构造 Prompt。
     *
     * @param question 用户问题
     * @param context 检索到的上下文片段
     * @return 可直接发送给模型的提示词对象
     */
    Prompt build(String question, List<RetrievedChunk> context, List<ConversationService.MessageView> history);

    /**
     * 模型请求所需的提示词结构。
     *
     * @param systemMessage 系统提示词
     * @param userMessage 用户提示词
     */
    record Prompt(String systemMessage, String userMessage) {
    }
}