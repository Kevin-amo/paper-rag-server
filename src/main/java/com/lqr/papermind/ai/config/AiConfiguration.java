package com.lqr.papermind.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 相关 Bean 配置。
 *
 * 这里把底层 Spring AI 的 ChatClient 显式暴露出来，业务服务只依赖统一封装后的接口。
 */
@Configuration
public class AiConfiguration {

    /**
     * 通过自动配置的 ChatModel 创建 ChatClient。
     *
     * @param chatModel Spring AI 自动装配的聊天模型
     * @return ChatClient 客户端
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.create(chatModel);
    }
}