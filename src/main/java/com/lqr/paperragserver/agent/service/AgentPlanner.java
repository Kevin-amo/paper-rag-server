package com.lqr.paperragserver.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.paperragserver.agent.model.AgentActionType;
import com.lqr.paperragserver.agent.model.AgentDecision;
import com.lqr.paperragserver.agent.model.AgentStepTrace;
import com.lqr.paperragserver.agent.tool.AgentToolRegistry;
import com.lqr.paperragserver.ai.service.LlmService;
import com.lqr.paperragserver.ai.service.PromptConstructionService;
import com.lqr.paperragserver.conversation.service.ConversationService;
import com.lqr.paperragserver.literature.support.LiteratureSearchIntentParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AgentPlanner {

    private final LlmService llmService;
    private final ObjectMapper objectMapper;
    private final AgentToolRegistry toolRegistry;
    private final LiteratureSearchIntentParser intentParser;

    public AgentDecision decide(String question,
                                List<ConversationService.MessageView> history,
                                List<AgentStepTrace> steps,
                                List<String> observations,
                                Integer topK) {
        try {
            String content = llmService.generate(buildDecisionPrompt(question, history, steps, observations, topK));
            return parseDecision(content, question, topK);
        } catch (RuntimeException ex) {
            return fallbackDecision(question, observations, topK);
        } catch (Exception ex) {
            return fallbackDecision(question, observations, topK);
        }
    }

    public String finalAnswer(String question,
                              List<ConversationService.MessageView> history,
                              List<AgentStepTrace> steps,
                              List<String> observations) {
        try {
            String answer = llmService.generate(buildFinalAnswerPrompt(question, history, steps, observations));
            if (answer == null || answer.isBlank()) {
                return fallbackAnswerFromObservations(observations);
            }
            return answer.trim();
        } catch (RuntimeException ex) {
            return fallbackAnswerFromObservations(observations);
        }
    }

    public Flux<String> finalAnswerStream(String question,
                                          List<ConversationService.MessageView> history,
                                          List<AgentStepTrace> steps,
                                          List<String> observations) {
        return llmService.streamGenerate(buildFinalAnswerPrompt(question, history, steps, observations))
                .filter(delta -> delta != null && !delta.isEmpty());
    }

    private PromptConstructionService.Prompt buildDecisionPrompt(String question,
                                                                 List<ConversationService.MessageView> history,
                                                                 List<AgentStepTrace> steps,
                                                                 List<String> observations,
                                                                 Integer topK) {
        String system = "你是论文超级智能体的 ReAct 决策器。你只负责选择下一步动作，不直接长篇回答。"
                + "\n可用工具：\n" + toolRegistry.toolDescriptions()
                + "\n\n规则："
                + "\n1. 用户问已上传论文、知识库、文档内容、总结、引用、方法对比时，优先 action=local_paper_retrieval。"
                + "\n2. 用户问找论文、搜文献、推荐文章、最新研究、外部资料时，优先 action=literature_search。"
                + "\n3. 用户问综述、研究现状、趋势、对比分析时，先判断资料范围：若目标指向已上传论文、本地知识库、当前文档或本文内容，只能 action=local_paper_retrieval；只有用户明确要求找新论文、搜外部文献、补充外部资料或最新研究时，才可 action=literature_search。"
                + "\n4. local_paper_retrieval 的 topK 只表示本地 RAG 检索时最多返回的片段数量配置，不代表本地库数量、论文数量或已检索结果。"
                + "\n5. literature_search 不使用 topK；外部文献数量只由用户明确说的“几篇/limit”决定，未明确时默认 limit=5。"
                + "\n6. 已有观察后，当前观察可用于回答时必须输出 finish=true；不要连续选择上一步相同 action。"
                + "\n7. thoughtSummary 只能是可展示的简短思考摘要，不要输出完整隐私思维链。"
                + "\n8. 只输出 JSON，不要 Markdown，不要解释。";
        String user = "用户目标：\n" + question
                + "\n\n本地 RAG 片段数配置 topK：" + (topK == null ? "默认" : topK)
                + "（仅 local_paper_retrieval 使用；这是配置参数，不代表本地库数量、论文数量或检索结果，禁止用于 literature_search）"
                + "\n\n最近会话：\n" + formatHistory(history)
                + "\n\n已执行步骤：\n" + formatSteps(steps)
                + "\n\n观察结果：\n" + formatObservations(observations)
                + "\n\n输出 JSON 格式："
                + "\n本地 RAG：{\"thoughtSummary\":\"...\",\"action\":\"local_paper_retrieval\",\"actionInput\":{\"query\":\"...\",\"topK\":5},\"finish\":false,\"answer\":null}"
                + "\n外部文献：{\"thoughtSummary\":\"...\",\"action\":\"literature_search\",\"actionInput\":{\"query\":\"...\",\"limit\":5,\"sortBy\":\"relevance|date\",\"dateFrom\":null},\"finish\":false,\"answer\":null}"
                + "\n结束：{\"thoughtSummary\":\"...\",\"action\":\"finish\",\"actionInput\":{},\"finish\":true,\"answer\":\"...\"}"
                + "\n如果 finish=true，可以给一个很短的 answer 草稿；最终回答会由后续生成器完成。";
        return new PromptConstructionService.Prompt(system, user);
    }

    private PromptConstructionService.Prompt buildFinalAnswerPrompt(String question,
                                                                    List<ConversationService.MessageView> history,
                                                                    List<AgentStepTrace> steps,
                                                                    List<String> observations) {
        String system = "你是论文超级智能体。请基于工具观察结果回答用户目标。"
                + "\n要求："
                + "\n1. 不编造未被观察结果支持的事实。"
                + "\n2. 区分本地知识库证据和外部文献搜索结果。"
                + "\n3. 如果证据不足，明确说明不足并给出下一步建议。"
                + "\n4. 用清晰的小标题和要点回答。";
        String user = "用户目标：\n" + question
                + "\n\n最近会话：\n" + formatHistory(history)
                + "\n\n执行步骤：\n" + formatSteps(steps)
                + "\n\n工具观察证据：\n" + formatObservations(observations)
                + "\n\n请输出最终回答：";
        return new PromptConstructionService.Prompt(system, user);
    }

    private AgentDecision parseDecision(String content, String question, Integer topK) throws Exception {
        JsonNode root = objectMapper.readTree(jsonObject(content));
        String thought = text(root, "thoughtSummary", "我会根据目标选择下一步工具。");
        boolean finish = root.path("finish").asBoolean(false);
        String actionText = text(root, "action", finish ? "finish" : null);
        AgentActionType action = AgentActionType.from(actionText);
        Map<String, Object> input = new LinkedHashMap<>();
        JsonNode inputNode = root.path("actionInput");
        if (inputNode.isObject()) {
            for (Map.Entry<String, JsonNode> entry : inputNode.properties()) {
                input.put(entry.getKey(), objectMapper.convertValue(entry.getValue(), Object.class));
            }
        }
        input.putIfAbsent("query", question);
        applyDeterministicLiteratureHints(action, input, question);
        applyTopK(action, input, topK);
        String answer = text(root, "answer", null);
        return new AgentDecision(thought, action, input, finish || action == AgentActionType.FINISH, answer);
    }

    private AgentDecision fallbackDecision(String question,
                                           List<String> observations,
                                           Integer topK) {
        if (observations != null && !observations.isEmpty()) {
            return AgentDecision.finish("已有工具观察，直接整理当前结果。", fallbackAnswerFromObservations(observations));
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("query", question);
        if (looksLikeLiteratureSearch(question)) {
            applyDeterministicLiteratureHints(AgentActionType.LITERATURE_SEARCH, input, question);
            input.putIfAbsent("limit", 5);
            input.remove("topK");
            return new AgentDecision("这是文献搜索类目标，我会先搜索外部论文。", AgentActionType.LITERATURE_SEARCH, input, false, null);
        }
        applyTopK(AgentActionType.LOCAL_PAPER_RETRIEVAL, input, topK);
        return new AgentDecision("这是本地论文分析类目标，我会先检索知识库。", AgentActionType.LOCAL_PAPER_RETRIEVAL, input, false, null);
    }

    private String fallbackAnswerFromObservations(List<String> observations) {
        if (observations == null || observations.isEmpty()) {
            return "当前模型暂不可用，且还没有可用于回答的检索结果。请稍后重试，或先补充更具体的检索目标。";
        }
        String evidence = String.join("\n\n", observations).trim();
        if (evidence.isBlank()) {
            return "已完成检索，但没有得到可用于回答的有效内容。";
        }
        return "已完成检索。当前模型暂不可用，先返回工具检索到的原始结果：\n\n" + cut(evidence, 6000);
    }

    private String cut(String content, int maxLength) {
        if (content == null) {
            return "";
        }
        String normalized = content.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
    }

    private void applyTopK(AgentActionType action, Map<String, Object> input, Integer topK) {
        if (action == AgentActionType.LOCAL_PAPER_RETRIEVAL) {
            if (topK != null) {
                input.putIfAbsent("topK", topK);
            }
            return;
        }
        input.remove("topK");
    }

    private void applyDeterministicLiteratureHints(AgentActionType action, Map<String, Object> input, String question) {
        if (action != AgentActionType.LITERATURE_SEARCH) {
            return;
        }
        LiteratureSearchIntentParser.Intent intent = intentParser.parse(question);
        if (intent.query() != null && !intent.query().isBlank()) {
            input.put("query", intent.query());
        }
        if (intent.limit() != null) {
            input.put("limit", intent.limit());
        }
        if (intent.sortBy() != null && !intent.sortBy().isBlank()) {
            input.put("sortBy", intent.sortBy());
        }
    }

    private boolean looksLikeLiteratureSearch(String question) {
        if (question == null) {
            return false;
        }
        String value = question.toLowerCase();
        return value.contains("找")
                || value.contains("搜")
                || value.contains("推荐")
                || value.contains("最新")
                || value.contains("文献")
                || value.contains("论文") && value.contains("外部")
                || value.contains("search")
                || value.contains("recommend")
                || value.contains("latest");
    }

    private String formatHistory(List<ConversationService.MessageView> history) {
        if (history == null || history.isEmpty()) {
            return "(无历史)";
        }
        return history.stream()
                .map(message -> message.role() + "：" + message.content())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("(无历史)");
    }

    private String formatSteps(List<AgentStepTrace> steps) {
        if (steps == null || steps.isEmpty()) {
            return "(尚未执行)";
        }
        return steps.stream()
                .map(step -> step.index() + ". " + step.thoughtSummary() + " -> " + step.action() + " -> " + step.observationSummary())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("(尚未执行)");
    }

    private String formatObservations(List<String> observations) {
        if (observations == null || observations.isEmpty()) {
            return "(暂无观察)";
        }
        return String.join("\n\n", observations);
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