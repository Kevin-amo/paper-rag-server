package com.lqr.paperragserver.ai.impl;

import com.lqr.paperragserver.ai.service.PromptConstructionService;
import com.lqr.paperragserver.common.model.RetrievedChunk;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 默认提示词构造实现。
 */
@Service
public class PromptConstructionServiceImpl implements PromptConstructionService {

    /**
     * 将问题和检索上下文拼装为统一提示词。
     *
     * @param question 用户问题
     * @param context 检索得到的上下文分块
     * @return 可直接发送给大模型的提示词对象
     */
    @Override
    public Prompt build(String question, List<RetrievedChunk> context) {
        String systemMessage = "你是一个严谨的论文问答助手。必须只依据给定上下文回答，无法确定时直接说明不确定。";
        String contextText = context == null || context.isEmpty()
                ? ""
                : context.stream()
                .map(item -> "[" + item.chunk().chunkId() + "] " + item.chunk().content())
                .collect(Collectors.joining("\n\n"));
        String userMessage = "问题：" + question + "\n\n可用上下文：\n" + contextText + "\n\n请给出简洁、可验证的回答，并在末尾列出引用片段编号。";
        return new Prompt(systemMessage, userMessage);
    }
}