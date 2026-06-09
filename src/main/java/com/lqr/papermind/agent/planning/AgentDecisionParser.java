package com.lqr.papermind.agent.planning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.papermind.agent.core.AgentActionType;
import com.lqr.papermind.agent.core.AgentDecision;
import com.lqr.papermind.agent.paper.LiteratureContextPolicy;
import com.lqr.papermind.literature.model.LiteratureSearchContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AgentDecisionParser {

    private final ObjectMapper objectMapper;
    private final LiteratureContextPolicy literatureContextPolicy;

    /**
     * 将模型输出解析为智能体决策，并补齐工具输入中的上下文参数。
     *
     * @param content               模型原始输出
     * @param question              用户当前问题
     * @param lastLiteratureContext 最近一次文献搜索上下文
     * @param topK                  本地检索片段数量配置
     * @return 智能体决策
     */
    public AgentDecision parse(String content,
                               String question,
                               LiteratureSearchContext lastLiteratureContext,
                               Integer topK) {
        try {
            JsonNode root = objectMapper.readTree(jsonObject(content));
            String thought = text(root, "thoughtSummary", "我会根据目标选择下一步工具。");
            boolean finish = root.path("finish").asBoolean(false);
            String actionText = text(root, "action", finish ? "finish" : null);
            AgentActionType action = AgentActionType.from(actionText);
            Map<String, Object> input = actionInput(root.path("actionInput"));
            input.putIfAbsent("query", question);
            if (action == AgentActionType.LITERATURE_SEARCH) {
                literatureContextPolicy.applySearchHints(input, question, lastLiteratureContext);
            }
            applyTopK(action, input, topK);
            String answer = text(root, "answer", null);
            return new AgentDecision(thought, action, input, finish || action == AgentActionType.FINISH, answer);
        } catch (Exception ex) {
            throw new IllegalArgumentException("无法解析 Agent 决策", ex);
        }
    }

    /**
     * 根据动作类型补齐或移除 topK 参数，避免外部文献搜索误用本地检索配置。
     *
     * @param action 动作类型
     * @param input  工具输入参数
     * @param topK   本地检索片段数量配置
     */
    static void applyTopK(AgentActionType action, Map<String, Object> input, Integer topK) {
        if (action == AgentActionType.LOCAL_PAPER_RETRIEVAL) {
            if (topK != null) {
                input.put("topK", topK);
            }
            return;
        }
        input.remove("topK");
    }

    /**
     * 将 JSON 中的 actionInput 节点转换为普通 Map。
     *
     * @param inputNode actionInput 节点
     * @return 工具输入参数
     */
    private Map<String, Object> actionInput(JsonNode inputNode) {
        Map<String, Object> input = new LinkedHashMap<>();
        if (!inputNode.isObject()) {
            return input;
        }
        for (Map.Entry<String, JsonNode> entry : inputNode.properties()) {
            input.put(entry.getKey(), objectMapper.convertValue(entry.getValue(), Object.class));
        }
        return input;
    }

    /**
     * 从 JSON 字段中读取非空文本，缺失时使用兜底值。
     *
     * @param root     JSON 根节点
     * @param field    字段名
     * @param fallback 兜底文本
     * @return 字段文本或兜底文本
     */
    private String text(JsonNode root, String field, String fallback) {
        JsonNode node = root.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    /**
     * 从模型输出中截取 JSON 对象文本，兼容带有额外说明的响应。
     *
     * @param content 模型原始输出
     * @return JSON 对象文本
     */
    private String jsonObject(String content) {
        if (content == null || content.isBlank()) {
            return "{}";
        }
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return content;
    }
}