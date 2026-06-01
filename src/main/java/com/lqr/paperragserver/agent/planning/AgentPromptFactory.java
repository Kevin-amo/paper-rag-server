package com.lqr.paperragserver.agent.planning;

import com.lqr.paperragserver.agent.core.AgentStep;
import com.lqr.paperragserver.agent.tool.AgentToolRegistry;
import com.lqr.paperragserver.ai.service.PromptConstructionService;
import com.lqr.paperragserver.conversation.service.ConversationService;
import com.lqr.paperragserver.literature.model.LiteratureSearchContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AgentPromptFactory {

    private final AgentToolRegistry toolRegistry;

    public PromptConstructionService.Prompt decisionPrompt(String question,
                                                           List<ConversationService.MessageView> history,
                                                           LiteratureSearchContext lastLiteratureContext,
                                                           List<AgentStep> steps,
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
                + "\n7. 文献搜索追问规则：如果用户当前问题是“有吗 / 有没有 / 这些里面 / 再找几篇 / 最新的 / 2026 年的 / 换成某方向”等追问，优先继承最近一次文献搜索状态里的 query。"
                + "\n8. 如果当前问题只是年份、数量、排序、筛选条件，不要把当前问题本身当 query。"
                + "\n9. 如果用户明确提出新主题，才覆盖最近一次文献搜索 query。"
                + "\n10. literature_search 的 actionInput 必须输出合并后的 query、limit、sortBy、dateFrom、dateTo、categories。"
                + "\n11. 如果最近一次文献搜索状态的 items 已能直接筛选回答，允许 action=finish 且 finish=true，不必重复搜索。"
                + "\n12. thoughtSummary 只能是可展示的简短思考摘要，不要输出完整隐私思维链。"
                + "\n13. 只输出 JSON，不要 Markdown，不要解释。";
        String user = "用户目标：\n" + question
                + "\n\n本地 RAG 片段数配置 topK：" + (topK == null ? "默认" : topK)
                + "（仅 local_paper_retrieval 使用；这是配置参数，不代表本地库数量、论文数量或检索结果，禁止用于 literature_search）"
                + "\n\n最近会话：\n" + formatHistory(history)
                + "\n\n最近一次文献搜索状态：\n" + formatLiteratureContext(lastLiteratureContext)
                + "\n\n已执行步骤：\n" + formatSteps(steps)
                + "\n\n观察结果：\n" + formatObservations(observations)
                + "\n\n输出 JSON 格式："
                + "\n本地 RAG：{\"thoughtSummary\":\"...\",\"action\":\"local_paper_retrieval\",\"actionInput\":{\"query\":\"...\",\"topK\":" + (topK == null ? "默认配置" : topK) + "},\"finish\":false,\"answer\":null}"
                + "\n外部文献：{\"thoughtSummary\":\"...\",\"action\":\"literature_search\",\"actionInput\":{\"query\":\"...\",\"limit\":5,\"sortBy\":\"relevance|date\",\"dateFrom\":null,\"dateTo\":null,\"categories\":[]},\"finish\":false,\"answer\":null}"
                + "\n结束：{\"thoughtSummary\":\"...\",\"action\":\"finish\",\"actionInput\":{},\"finish\":true,\"answer\":\"...\"}"
                + "\n如果 finish=true，可以给一个很短的 answer 草稿；最终回答会由后续生成器完成。";
        return new PromptConstructionService.Prompt(system, user);
    }

    public PromptConstructionService.Prompt finalAnswerPrompt(String question,
                                                              List<ConversationService.MessageView> history,
                                                              List<AgentStep> steps,
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

    private String formatHistory(List<ConversationService.MessageView> history) {
        if (history == null || history.isEmpty()) {
            return "(无历史)";
        }
        return history.stream()
                .map(message -> message.role() + "：" + message.content())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("(无历史)");
    }

    private String formatLiteratureContext(LiteratureSearchContext context) {
        if (context == null) {
            return "(无文献搜索状态)";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("query=").append(context.query());
        builder.append("\nlimit=").append(context.limit());
        builder.append("\nsortBy=").append(context.sortBy());
        builder.append("\ndateFrom=").append(context.dateFrom());
        builder.append("\ndateTo=").append(context.dateTo());
        builder.append("\ncategories=").append(context.categories());
        builder.append("\nitemsCount=").append(context.items().size());
        if (!context.items().isEmpty()) {
            builder.append("\nitems摘要：");
            context.items().stream().limit(5).forEach(item -> builder
                    .append("\n- ")
                    .append(item.title())
                    .append(" (")
                    .append(item.year())
                    .append(")"));
        }
        return builder.toString();
    }

    private String formatSteps(List<AgentStep> steps) {
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
}