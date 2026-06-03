package com.lqr.paperragserver.ai.service.impl;

import com.lqr.paperragserver.ai.service.LlmService;
import com.lqr.paperragserver.ai.service.PromptConstructionService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 基于 Spring AI ChatClient 的大模型调用实现。
 */
@Service
@RequiredArgsConstructor
public class LlmServiceImpl implements LlmService {

    private final ChatClient chatClient;
    private final List<ToolCallbackProvider> toolCallbackProviders;

    /**
     * 使用构造好的提示词生成回答内容。
     *
     * @param prompt 包含 system 与 user 消息的提示词对象
     * @return 模型返回的文本内容
     */
    @Override
    public String generate(PromptConstructionService.Prompt prompt) {
        return request(prompt)
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
        return request(prompt)
                .stream()
                .content();
    }

    /**
     * 使用构造好的提示词并允许模型调用已注册工具生成回答内容。
     *
     * @param prompt 包含 system 与 user 消息的提示词对象
     * @return 模型返回的文本内容
     */
    @Override
    public String generateWithTools(PromptConstructionService.Prompt prompt) {
        return withTools(request(prompt))
                .call()
                .content();
    }

    /**
     * 使用构造好的提示词并允许模型调用已注册工具流式生成回答内容。
     *
     * @param prompt 包含 system 与 user 消息的提示词对象
     * @return 模型返回的增量文本流
     */
    @Override
    public Flux<String> streamGenerateWithTools(PromptConstructionService.Prompt prompt) {
        return withTools(request(prompt))
                .stream()
                .content();
    }

    /**
     * 根据提示词对象构建 ChatClient 请求规格。
     *
     * @param prompt 包含 system 与 user 消息的提示词对象
     * @return ChatClient 请求规格
     */
    private ChatClientRequestSpec request(PromptConstructionService.Prompt prompt) {
        return chatClient.prompt()
                .messages(
                        new SystemMessage(prompt.systemMessage()),
                        new UserMessage(prompt.userMessage())
                );
    }

    /**
     * 为请求规格注册所有可用的工具回调提供者。
     *
     * @param requestSpec 原始请求规格
     * @return 注册了工具回调的请求规格，无可用工具时返回原始规格
     */
    private ChatClientRequestSpec withTools(ChatClientRequestSpec requestSpec) {
        if (toolCallbackProviders == null || toolCallbackProviders.isEmpty()) {
            return requestSpec;
        }
        return requestSpec.toolCallbacks(toolCallbackProviders.toArray(ToolCallbackProvider[]::new));
    }
}