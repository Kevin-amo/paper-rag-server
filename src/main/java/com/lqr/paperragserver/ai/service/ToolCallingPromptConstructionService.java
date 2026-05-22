package com.lqr.paperragserver.ai.service;

/**
 * 工具调用类提示词构造服务。
 */
public interface ToolCallingPromptConstructionService {

    /**
     * 构造文献搜索工具调用提示词。
     *
     * @param userInput 用户自然语言输入
     * @return 可直接发送给模型的提示词对象
     */
    PromptConstructionService.Prompt buildLiteratureSearchToolCallPrompt(String userInput);

    /**
     * 构造文献搜索请求解析提示词。
     *
     * @param userInput 用户自然语言输入
     * @return 可直接发送给模型的提示词对象
     */
    PromptConstructionService.Prompt buildLiteratureSearchPlanPrompt(String userInput);
}