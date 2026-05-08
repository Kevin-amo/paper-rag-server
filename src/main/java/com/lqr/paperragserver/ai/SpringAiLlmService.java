package com.lqr.paperragserver.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

/**
 * 基于 Spring AI ChatClient 的大模型调用实现。
 */
@Service
public class SpringAiLlmService implements LlmService {

    private final ChatClient chatClient;

    public SpringAiLlmService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

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
}