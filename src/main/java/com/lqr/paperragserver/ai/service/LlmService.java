package com.lqr.paperragserver.ai.service;

import reactor.core.publisher.Flux;

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

    /**
     * 调用大模型流式生成回答。
     *
     * @param prompt 需要发送给模型的提示词内容
     * @return 模型生成的增量文本流
     */
    Flux<String> streamGenerate(PromptConstructionService.Prompt prompt);
}