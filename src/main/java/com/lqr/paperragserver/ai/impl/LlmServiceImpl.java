package com.lqr.paperragserver.ai.impl;

import com.lqr.paperragserver.ai.service.LlmService;
import com.lqr.paperragserver.ai.service.PromptConstructionService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 基于 Spring AI ChatClient 的大模型调用实现。
 */
@Service
@RequiredArgsConstructor
public class LlmServiceImpl implements LlmService {

    private final ChatClient chatClient;

    /**
     * 使用构造好的提示词生成回答内容。
     *
     * @param prompt 包含 system 与 user 消息的提示词对象
     * @return 模型返回的文本内容
     */
    @Override
    public String generate(PromptConstructionService.Prompt prompt) {
        return chatClient.prompt()
                .messages(
                        new SystemMessage(prompt.systemMessage()),
                        new UserMessage(prompt.userMessage())
                )
                .call()
                .content();
    }

    /**
     * 使用构造好的提示词流式生成回答内容。
     *
     * @param prompt 包含 system 与 user 消息的提示词对象
     * @return 模型返回的增量文本流
     */
    @Override
    public Flux<String> streamGenerate(PromptConstructionService.Prompt prompt) {
        return chatClient.prompt()
                .messages(
                        new SystemMessage(prompt.systemMessage()),
                        new UserMessage(prompt.userMessage())
                )
                .stream()
                .content();
    }
}