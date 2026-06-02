package com.lqr.paperragserver.agent.planning;

import com.lqr.paperragserver.agent.core.AgentDecision;
import com.lqr.paperragserver.agent.core.AgentStep;
import com.lqr.paperragserver.agent.paper.LiteratureContextPolicy;
import com.lqr.paperragserver.ai.service.LlmService;
import com.lqr.paperragserver.ai.service.PromptConstructionService;
import com.lqr.paperragserver.common.logging.LogSanitizer;
import com.lqr.paperragserver.conversation.service.ConversationService;
import com.lqr.paperragserver.literature.model.LiteratureSearchContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentPlanner {

    private final LlmService llmService;
    private final AgentPromptFactory promptFactory;
    private final AgentDecisionParser decisionParser;
    private final AgentFallbackPolicy fallbackPolicy;
    private final LiteratureContextPolicy literatureContextPolicy;
    private final AgentHybridTaskPolicy hybridTaskPolicy;

    /**
     * 基于用户目标和当前执行状态生成下一步智能体决策。
     *
     * @param question     用户当前目标
     * @param history      最近会话历史
     * @param steps        已执行步骤
     * @param observations 工具观察结果
     * @param topK         本地检索片段数量配置
     * @return 智能体下一步决策
     */
    public AgentDecision decide(String question,
                                List<ConversationService.MessageView> history,
                                List<AgentStep> steps,
                                List<String> observations,
                                Integer topK) {
        return decide(question, history, null, steps, observations, topK);
    }

    /**
     * 基于用户目标、历史和文献上下文生成下一步智能体决策。
     *
     * @param question              用户当前目标
     * @param history               最近会话历史
     * @param lastLiteratureContext 最近一次文献搜索上下文
     * @param steps                 已执行步骤
     * @param observations          工具观察结果
     * @param topK                  本地检索片段数量配置
     * @return 智能体下一步决策
     */
    public AgentDecision decide(String question,
                                List<ConversationService.MessageView> history,
                                LiteratureSearchContext lastLiteratureContext,
                                List<AgentStep> steps,
                                List<String> observations,
                                Integer topK) {
        long startNanos = System.nanoTime();
        log.info("agent.plan.start questionLength={} questionExcerpt={} historyCount={} stepsCount={} observationsCount={} topK={}",
                textLength(question), LogSanitizer.safeExcerpt(question, 160), size(history), size(steps), size(observations), topK);
        try {
            AgentDecision hybridDecision = hybridTaskPolicy.decide(question, steps, topK);
            if (hybridDecision != null) {
                log.info("agent.plan.done action={} finish={} actionInputSummary={} reason=HYBRID_TASK costMs={}",
                        hybridDecision.action(), hybridDecision.finish(), LogSanitizer.safeActionInput(hybridDecision.actionInput()), elapsedMs(startNanos));
                return hybridDecision;
            }
            PromptConstructionService.Prompt prompt = promptFactory.decisionPrompt(question, history, lastLiteratureContext, steps, observations, topK);
            log.debug("agent.plan.prompt questionExcerpt={} promptSystemExcerpt={} promptUserExcerpt={}",
                    LogSanitizer.safeExcerpt(question, 160), LogSanitizer.safeExcerpt(prompt.systemMessage(), 500), LogSanitizer.safeExcerpt(prompt.userMessage(), 500));
            AgentDecision contextDecision = literatureContextPolicy.finishFromPreviousItems(question, lastLiteratureContext, observations);
            if (contextDecision != null) {
                log.info("agent.plan.done action={} finish={} actionInputSummary={} reason=PREVIOUS_LITERATURE_ITEMS costMs={}",
                        contextDecision.action(), contextDecision.finish(), LogSanitizer.safeActionInput(contextDecision.actionInput()), elapsedMs(startNanos));
                return contextDecision;
            }
            String content = llmService.generate(prompt);
            AgentDecision decision = decisionParser.parse(content, question, lastLiteratureContext, topK);
            log.info("agent.plan.done action={} finish={} actionInputSummary={} costMs={}",
                    decision.action(), decision.finish(), LogSanitizer.safeActionInput(decision.actionInput()), elapsedMs(startNanos));
            log.debug("agent.plan.response action={} finish={} answerExcerpt={}",
                    decision.action(), decision.finish(), LogSanitizer.safeExcerpt(decision.answer(), 500));
            return decision;
        } catch (RuntimeException ex) {
            log.warn("agent.plan.fallback questionLength={} observationsCount={} topK={} reason=RUNTIME_EXCEPTION costMs={}",
                    textLength(question), size(observations), topK, elapsedMs(startNanos), ex);
            return fallbackPolicy.decision(question, steps, observations, lastLiteratureContext, topK);
        }
    }

    /**
     * 基于工具观察生成非流式最终回答。
     *
     * @param question     用户当前目标
     * @param history      最近会话历史
     * @param steps        已执行步骤
     * @param observations 工具观察结果
     * @return 最终回答文本
     */
    public String finalAnswer(String question,
                              List<ConversationService.MessageView> history,
                              List<AgentStep> steps,
                              List<String> observations) {
        long startNanos = System.nanoTime();
        try {
            PromptConstructionService.Prompt prompt = promptFactory.finalAnswerPrompt(question, history, steps, observations);
            log.debug("agent.answer.prompt questionExcerpt={} promptSystemExcerpt={} promptUserExcerpt={}",
                    LogSanitizer.safeExcerpt(question, 160), LogSanitizer.safeExcerpt(prompt.systemMessage(), 500), LogSanitizer.safeExcerpt(prompt.userMessage(), 500));
            String answer = llmService.generate(prompt);
            if (answer == null || answer.isBlank()) {
                log.warn("agent.answer.fallback reason=EMPTY_ANSWER observationsCount={} costMs={}", size(observations), elapsedMs(startNanos));
                return fallbackPolicy.answerFromObservations(observations);
            }
            log.info("agent.answer.done answerLength={} stepsCount={} observationsCount={} costMs={}",
                    answer.trim().length(), size(steps), size(observations), elapsedMs(startNanos));
            log.debug("agent.answer.response answerExcerpt={}", LogSanitizer.safeExcerpt(answer, 500));
            return answer.trim();
        } catch (RuntimeException ex) {
            log.warn("agent.answer.fallback reason=RUNTIME_EXCEPTION observationsCount={} costMs={}", size(observations), elapsedMs(startNanos), ex);
            return fallbackPolicy.answerFromObservations(observations);
        }
    }

    /**
     * 基于工具观察生成流式最终回答。
     *
     * @param question     用户当前目标
     * @param history      最近会话历史
     * @param steps        已执行步骤
     * @param observations 工具观察结果
     * @return 回答文本增量流
     */
    public Flux<String> finalAnswerStream(String question,
                                          List<ConversationService.MessageView> history,
                                          List<AgentStep> steps,
                                          List<String> observations) {
        PromptConstructionService.Prompt prompt = promptFactory.finalAnswerPrompt(question, history, steps, observations);
        log.debug("agent.answer.stream.prompt questionExcerpt={} promptSystemExcerpt={} promptUserExcerpt={}",
                LogSanitizer.safeExcerpt(question, 160), LogSanitizer.safeExcerpt(prompt.systemMessage(), 500), LogSanitizer.safeExcerpt(prompt.userMessage(), 500));
        return llmService.streamGenerate(prompt)
                .filter(delta -> delta != null && !delta.isEmpty());
    }

    /**
     * 计算列表大小，空列表引用按 0 处理。
     *
     * @param items 待统计列表
     * @return 列表大小
     */
    private int size(List<?> items) {
        return items == null ? 0 : items.size();
    }

    /**
     * 计算文本长度，空文本引用按 0 处理。
     *
     * @param text 待统计文本
     * @return 文本长度
     */
    private int textLength(String text) {
        return text == null ? 0 : text.length();
    }

    /**
     * 将纳秒起点换算为毫秒耗时，用于日志记录。
     *
     * @param startNanos 起始纳秒时间
     * @return 已经过的毫秒数
     */
    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}