package com.lqr.paperragserver.ai.config;

import com.lqr.paperragserver.ai.tool.LiteratureSearchTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiToolConfiguration {

    @Bean
    public ToolCallbackProvider literatureSearchToolCallbackProvider(LiteratureSearchTool literatureSearchTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(literatureSearchTool)
                .build();
    }
}