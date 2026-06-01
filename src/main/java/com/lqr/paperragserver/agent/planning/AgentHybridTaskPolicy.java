package com.lqr.paperragserver.agent.planning;

import com.lqr.paperragserver.agent.core.AgentActionType;
import com.lqr.paperragserver.agent.core.AgentDecision;
import com.lqr.paperragserver.agent.core.AgentStep;
import com.lqr.paperragserver.literature.support.LiteratureSearchIntentParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class AgentHybridTaskPolicy {

    public static final String LITERATURE_TOOL = "literature_search";
    public static final String LOCAL_RETRIEVAL_TOOL = "local_paper_retrieval";

    private static final Pattern EXTERNAL_LITERATURE_INTENT = Pattern.compile(
            ".*((找|搜索|搜|推荐|检索|查找).*(论文|文献|研究|papers?|articles?|literature|research)|最新(论文|文献|研究)|latest\\s+(papers?|articles?|literature|research)|external\\s+(papers?|literature|research)).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern LOCAL_KNOWLEDGE_INTENT = Pattern.compile(
            ".*(结合(我的|本地)?知识库|本地知识库|已上传(文档|论文)|我的(文档|论文|知识库)|用我的(文档|论文|知识库)|根据我的(文档|论文|知识库)|基于我的(文档|论文|知识库)|local\\s+(knowledge|documents?|papers?)).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern TREND_INTENT = Pattern.compile(".*(趋势|研究趋势|发展趋势|trend|trends).*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final LiteratureSearchIntentParser intentParser;

    public boolean isHybrid(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String text = question.trim();
        return EXTERNAL_LITERATURE_INTENT.matcher(text).matches()
                && LOCAL_KNOWLEDGE_INTENT.matcher(text).matches();
    }

    public AgentDecision decide(String question, List<AgentStep> steps, Integer topK) {
        if (!isHybrid(question)) {
            return null;
        }
        if (!literatureDone(steps)) {
            return literatureSearchDecision(question);
        }
        if (!localRetrievalDone(steps)) {
            return localRetrievalDecision(question, topK);
        }
        return AgentDecision.finish("外部文献和本地知识库证据已完成，我将综合整理最终回答。", "已完成外部文献搜索和本地知识库检索，准备综合回答。");
    }

    public boolean shouldContinueAfterLiteratureUnavailable(String question, String toolName, List<AgentStep> steps) {
        return LITERATURE_TOOL.equals(toolName)
                && isHybrid(question)
                && !localRetrievalDone(steps);
    }

    private AgentDecision literatureSearchDecision(String question) {
        LiteratureSearchIntentParser.Intent intent = intentParser.parse(question);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("query", hasText(intent.query()) ? intent.query() : question);
        input.put("limit", intent.limit() == null ? 5 : intent.limit());
        input.put("sortBy", hasText(intent.sortBy()) ? intent.sortBy() : "relevance");
        input.put("dateFrom", hasText(intent.dateFrom()) ? intent.dateFrom() : null);
        input.put("dateTo", hasText(intent.dateTo()) ? intent.dateTo() : null);
        input.put("categories", intent.categories() == null ? List.of() : intent.categories());
        return new AgentDecision("这是外部文献和本地知识库复合任务，我会先搜索外部论文。", AgentActionType.LITERATURE_SEARCH, input, false, null);
    }

    private AgentDecision localRetrievalDecision(String question, Integer topK) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("query", localRetrievalQuery(question));
        AgentDecisionParser.applyTopK(AgentActionType.LOCAL_PAPER_RETRIEVAL, input, topK);
        return new AgentDecision("外部文献步骤已完成，我会继续检索本地知识库。", AgentActionType.LOCAL_PAPER_RETRIEVAL, input, false, null);
    }

    private String localRetrievalQuery(String question) {
        LiteratureSearchIntentParser.Intent intent = intentParser.parse(question);
        String base = hasText(intent.query()) ? intent.query().trim() : question == null ? "" : question.trim();
        if (base.isBlank()) {
            return question;
        }
        if (TREND_INTENT.matcher(question == null ? "" : question).matches() && !base.contains("趋势")) {
            return base + " 研究趋势";
        }
        return base;
    }

    private boolean literatureDone(List<AgentStep> steps) {
        return hasAction(steps, LITERATURE_TOOL);
    }

    private boolean localRetrievalDone(List<AgentStep> steps) {
        return hasAction(steps, LOCAL_RETRIEVAL_TOOL);
    }

    private boolean hasAction(List<AgentStep> steps, String action) {
        return steps != null && steps.stream().anyMatch(step -> action.equals(step.action()));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}