package com.lqr.paperragserver.ai.service.impl;

import com.lqr.paperragserver.ai.service.PromptConstructionService;
import com.lqr.paperragserver.ai.service.ToolCallingPromptConstructionService;
import org.springframework.stereotype.Service;

/**
 * 默认工具调用类提示词构造实现。
 */
@Service
public class ToolCallingPromptConstructionServiceImpl implements ToolCallingPromptConstructionService {

    @Override
    public PromptConstructionService.Prompt buildLiteratureSearchToolCallPrompt(String userInput) {
        String systemMessage = "你是文献搜索助手。用户要求搜索、查找、推荐论文/文献/文章时，必须调用文献搜索工具。最终只输出 JSON，不要输出 Markdown、解释或额外文本。";
        String userMessage = "用户输入：" + userInput
                + "\n\n请先从用户输入中抽取文献搜索工具参数："
                + "\n- query 只保留核心主题、方法名、作者名或标题关键词，例如 ‘给我搜一篇关于RAG的文章’ 的 query 是 ‘RAG’。"
                + "\n- 用户说一篇、一个、1篇、推荐一篇或 one 时，limit 使用 1；未指定数量时 limit 使用 5。"
                + "\n- 用户说最新、最近、近年、latest、recent、newest 时，sortBy 使用 date。"
                + "\n\n然后调用文献搜索工具。工具返回后，最终只返回如下 JSON 结构：{\"items\":[...]}。";
        return new PromptConstructionService.Prompt(systemMessage, userMessage);
    }

    @Override
    public PromptConstructionService.Prompt buildLiteratureSearchPlanPrompt(String userInput) {
        String systemMessage = "你是文献搜索请求解析器。你的任务是把用户自然语言转换为文献搜索工具参数。只输出 JSON，不要输出 Markdown、解释或额外文本。";
        String userMessage = "用户输入：" + userInput
                + "\n\n请输出 JSON 对象，字段如下："
                + "\n- query: string，核心检索词。必须去掉‘帮我找’、‘给我搜’、‘关于’、‘文献’、‘论文’、‘文章’等指令性词，只保留主题、方法名、作者名或标题关键词。"
                + "\n- limit: number|null，用户说一篇、一个、1篇、推荐一篇或 one 时填 1；未指定数量填 5。"
                + "\n- sortBy: \"relevance\"|\"date\"|null，用户说最新、最近、近年、latest、recent、newest 时填 date，否则填 null。"
                + "\n- dateFrom: string|null，若用户明确给出起始日期，格式 YYYY-MM-DD；否则填 null。"
                + "\n- categories: string[]，无法确定时返回空数组。"
                + "\n\n示例："
                + "\n输入：给我搜一篇关于RAG的文章"
                + "\n输出：{\"query\":\"RAG\",\"limit\":1,\"sortBy\":null,\"dateFrom\":null,\"categories\":[]}"
                + "\n\n现在只输出 JSON：";
        return new PromptConstructionService.Prompt(systemMessage, userMessage);
    }
}