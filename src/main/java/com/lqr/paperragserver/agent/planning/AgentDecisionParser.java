package com.lqr.paperragserver.agent.planning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.paperragserver.agent.core.AgentActionType;
import com.lqr.paperragserver.agent.core.AgentDecision;
import com.lqr.paperragserver.agent.paper.LiteratureContextPolicy;
import com.lqr.paperragserver.literature.model.LiteratureSearchContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AgentDecisionParser {

    private final ObjectMapper objectMapper;
    private final LiteratureContextPolicy literatureContextPolicy;

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

    static void applyTopK(AgentActionType action, Map<String, Object> input, Integer topK) {
        if (action == AgentActionType.LOCAL_PAPER_RETRIEVAL) {
            if (topK != null) {
                input.put("topK", topK);
            }
            return;
        }
        input.remove("topK");
    }

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

    private String text(JsonNode root, String field, String fallback) {
        JsonNode node = root.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? fallback : value.trim();
    }

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