package com.lqr.paperragserver.ai.service;

/**
 * 大模型调用服务接口。
 *
 * <p>实现类负责把构造好的 Prompt 发送给具体模型，并返回生成结果。</p>
 */
public interface LlmService {

    /**
     * 调用大模型生成回答。
     *
     * @param prompt 需要发送给模型的提示词内容
     * @return 模型生成的文本结果
     */
    String generate(PromptConstructionService.Prompt prompt);
}