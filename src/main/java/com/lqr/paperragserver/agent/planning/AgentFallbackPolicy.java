package com.lqr.paperragserver.agent.planning;

import com.lqr.paperragserver.agent.core.AgentActionType;
import com.lqr.paperragserver.agent.core.AgentDecision;
import com.lqr.paperragserver.agent.core.AgentStep;
import com.lqr.paperragserver.agent.paper.LiteratureFollowUpPolicy;
import com.lqr.paperragserver.common.logging.LogSanitizer;
import com.lqr.paperragserver.literature.model.LiteratureSearchContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AgentFallbackPolicy {

    private final LiteratureFollowUpPolicy literatureFollowUpPolicy;
    private final AgentHybridTaskPolicy hybridTaskPolicy;

    /**
     * 在模型不可用或决策失败时，根据问题类型选择可执行的兜底动作。
     *
     * @param question              用户当前问题
     * @param steps                 已执行步骤
     * @param observations          当前已有观察结果
     * @param lastLiteratureContext 最近一次文献搜索上下文
     * @param topK                  本地检索片段数量配置
     * @return 兜底智能体决策
     */
    public AgentDecision decision(String question,
                                  List<AgentStep> steps,
                                  List<String> observations,
                                  LiteratureSearchContext lastLiteratureContext,
                                  Integer topK) {
        AgentDecision hybridDecision = hybridTaskPolicy.decide(question, steps, topK);
        if (hybridDecision != null && !hybridDecision.finish()) {
            return hybridDecision;
        }
        if (observations != null && !observations.isEmpty()) {
            return AgentDecision.finish("已有工具观察，直接整理当前结果。", answerFromObservations(observations));
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("query", question);
        if (looksLikeLiteratureSearch(question) || literatureFollowUpPolicy.matches(question, lastLiteratureContext)) {
            literatureFollowUpPolicy.applyTo(input, question, lastLiteratureContext);
            input.putIfAbsent("limit", 5);
            input.remove("topK");
            return new AgentDecision("这是文献搜索类目标，我会先搜索外部论文。", AgentActionType.LITERATURE_SEARCH, input, false, null);
        }
        AgentDecisionParser.applyTopK(AgentActionType.LOCAL_PAPER_RETRIEVAL, input, topK);
        return new AgentDecision("这是本地论文分析类目标，我会先检索知识库。", AgentActionType.LOCAL_PAPER_RETRIEVAL, input, false, null);
    }

    /**
     * 在最终回答模型不可用时，将工具观察结果整理为可展示的兜底回答。
     *
     * @param observations 工具观察结果
     * @return 兜底回答文本
     */
    public String answerFromObservations(List<String> observations) {
        if (observations == null || observations.isEmpty()) {
            return "当前模型暂不可用，且还没有可用于回答的检索结果。请稍后重试，或先补充更具体的检索目标。";
        }
        String evidence = String.join("\n\n", observations).trim();
        if (evidence.isBlank()) {
            return "已完成检索，但没有得到可用于回答的有效内容。";
        }
        return "已完成检索。当前模型暂不可用，先返回工具检索到的原始结果：\n\n" + LogSanitizer.safeExcerpt(evidence, 6000);
    }

    /**
     * 粗略判断用户问题是否更适合走外部文献搜索。
     *
     * @param question 用户当前问题
     * @return 是否像文献搜索请求
     */
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
}