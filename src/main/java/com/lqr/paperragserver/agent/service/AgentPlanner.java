package com.lqr.paperragserver.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.paperragserver.agent.model.AgentActionType;
import com.lqr.paperragserver.agent.model.AgentDecision;
import com.lqr.paperragserver.agent.model.AgentStepTrace;
import com.lqr.paperragserver.agent.tool.AgentToolRegistry;
import com.lqr.paperragserver.ai.service.LlmService;
import com.lqr.paperragserver.ai.service.PromptConstructionService;
import com.lqr.paperragserver.common.logging.LogSanitizer;
import com.lqr.paperragserver.conversation.service.ConversationService;
import com.lqr.paperragserver.literature.model.LiteratureSearchContext;
import com.lqr.paperragserver.literature.model.LiteratureSearchResult;
import com.lqr.paperragserver.literature.support.LiteratureSearchIntentParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 论文智能体的决策与回答规划组件，负责根据用户目标、会话历史和工具观察选择下一步动作或组织最终回复。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentPlanner {

    private final LlmService llmService;
    private final ObjectMapper objectMapper;
    private final AgentToolRegistry toolRegistry;
    private final LiteratureSearchIntentParser intentParser;

    /**
     * 根据用户目标、历史消息和已有工具观察选择下一步动作，异常时回退到规则决策。
     *
     * @param question     用户本轮问题
     * @param history      最近会话历史
     * @param steps        已执行步骤轨迹
     * @param observations 已收集的工具观察
     * @param topK         本地检索片段数量配置
     * @return 下一步智能体决策
     */
    public AgentDecision decide(String question,
                                List<ConversationService.MessageView> history,
                                List<AgentStepTrace> steps,
                                List<String> observations,
                                Integer topK) {
        return decide(question, history, null, steps, observations, topK);
    }

    public AgentDecision decide(String question,
                                List<ConversationService.MessageView> history,
                                LiteratureSearchContext lastLiteratureContext,
                                List<AgentStepTrace> steps,
                                List<String> observations,
                                Integer topK) {
        long startNanos = System.nanoTime();
        log.info("agent.plan.start questionLength={} questionExcerpt={} historyCount={} stepsCount={} observationsCount={} topK={}",
                textLength(question), LogSanitizer.safeExcerpt(question, 160), size(history), size(steps), size(observations), topK);
        try {
            PromptConstructionService.Prompt prompt = buildDecisionPrompt(question, history, lastLiteratureContext, steps, observations, topK);
            log.debug("agent.plan.prompt questionExcerpt={} promptSystemExcerpt={} promptUserExcerpt={}",
                    LogSanitizer.safeExcerpt(question, 160), LogSanitizer.safeExcerpt(prompt.systemMessage(), 500), LogSanitizer.safeExcerpt(prompt.userMessage(), 500));
            AgentDecision contextDecision = finishFromPreviousLiteratureItems(question, lastLiteratureContext, observations);
            if (contextDecision != null) {
                log.info("agent.plan.done action={} finish={} actionInputSummary={} reason=PREVIOUS_LITERATURE_ITEMS costMs={}",
                        contextDecision.action(), contextDecision.finish(), LogSanitizer.safeActionInput(contextDecision.actionInput()), elapsedMs(startNanos));
                return contextDecision;
            }
            String content = llmService.generate(prompt);
            AgentDecision decision = parseDecision(content, question, lastLiteratureContext, topK);
            log.info("agent.plan.done action={} finish={} actionInputSummary={} costMs={}",
                    decision.action(), decision.finish(), LogSanitizer.safeActionInput(decision.actionInput()), elapsedMs(startNanos));
            log.debug("agent.plan.response action={} finish={} answerExcerpt={}",
                    decision.action(), decision.finish(), LogSanitizer.safeExcerpt(decision.answer(), 500));
            return decision;
        } catch (RuntimeException ex) {
            log.warn("agent.plan.fallback questionLength={} observationsCount={} topK={} reason=RUNTIME_EXCEPTION costMs={}",
                    textLength(question), size(observations), topK, elapsedMs(startNanos), ex);
            return fallbackDecision(question, observations, lastLiteratureContext, topK);
        } catch (Exception ex) {
            log.warn("agent.plan.fallback questionLength={} observationsCount={} topK={} reason=PARSE_EXCEPTION costMs={}",
                    textLength(question), size(observations), topK, elapsedMs(startNanos), ex);
            return fallbackDecision(question, observations, lastLiteratureContext, topK);
        }
    }

    /**
     * 基于工具观察生成非流式最终回答，模型不可用或空回答时返回兜底内容。
     *
     * @param question     用户本轮问题
     * @param history      最近会话历史
     * @param steps        执行步骤轨迹
     * @param observations 工具观察证据
     * @return 最终回答文本
     */
    public String finalAnswer(String question,
                              List<ConversationService.MessageView> history,
                              List<AgentStepTrace> steps,
                              List<String> observations) {
        long startNanos = System.nanoTime();
        try {
            PromptConstructionService.Prompt prompt = buildFinalAnswerPrompt(question, history, steps, observations);
            log.debug("agent.answer.prompt questionExcerpt={} promptSystemExcerpt={} promptUserExcerpt={}",
                    LogSanitizer.safeExcerpt(question, 160), LogSanitizer.safeExcerpt(prompt.systemMessage(), 500), LogSanitizer.safeExcerpt(prompt.userMessage(), 500));
            String answer = llmService.generate(prompt);
            if (answer == null || answer.isBlank()) {
                log.warn("agent.answer.fallback reason=EMPTY_ANSWER observationsCount={} costMs={}", size(observations), elapsedMs(startNanos));
                return fallbackAnswerFromObservations(observations);
            }
            log.info("agent.answer.done answerLength={} stepsCount={} observationsCount={} costMs={}",
                    answer.trim().length(), size(steps), size(observations), elapsedMs(startNanos));
            log.debug("agent.answer.response answerExcerpt={}", LogSanitizer.safeExcerpt(answer, 500));
            return answer.trim();
        } catch (RuntimeException ex) {
            log.warn("agent.answer.fallback reason=RUNTIME_EXCEPTION observationsCount={} costMs={}", size(observations), elapsedMs(startNanos), ex);
            return fallbackAnswerFromObservations(observations);
        }
    }

    /**
     * 基于工具观察生成流式最终回答，输出模型返回的非空增量片段。
     *
     * @param question     用户本轮问题
     * @param history      最近会话历史
     * @param steps        执行步骤轨迹
     * @param observations 工具观察证据
     * @return 最终回答增量流
     */
    public Flux<String> finalAnswerStream(String question,
                                          List<ConversationService.MessageView> history,
                                          List<AgentStepTrace> steps,
                                          List<String> observations) {
        PromptConstructionService.Prompt prompt = buildFinalAnswerPrompt(question, history, steps, observations);
        log.debug("agent.answer.stream.prompt questionExcerpt={} promptSystemExcerpt={} promptUserExcerpt={}",
                LogSanitizer.safeExcerpt(question, 160), LogSanitizer.safeExcerpt(prompt.systemMessage(), 500), LogSanitizer.safeExcerpt(prompt.userMessage(), 500));
        return llmService.streamGenerate(prompt)
                .filter(delta -> delta != null && !delta.isEmpty());
    }

    /**
     * 构造 ReAct 决策提示词，约束模型只输出下一步动作 JSON。
     *
     * @param question     用户本轮问题
     * @param history      最近会话历史
     * @param steps        已执行步骤轨迹
     * @param observations 已收集工具观察
     * @param topK         本地检索片段数量配置
     * @return 决策阶段提示词
     */
    private PromptConstructionService.Prompt buildDecisionPrompt(String question,
                                                                 List<ConversationService.MessageView> history,
                                                                 LiteratureSearchContext lastLiteratureContext,
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

    /**
     * 构造最终回答提示词，要求模型仅基于工具观察组织用户可读回复。
     *
     * @param question     用户本轮问题
     * @param history      最近会话历史
     * @param steps        执行步骤轨迹
     * @param observations 工具观察证据
     * @return 最终回答阶段提示词
     */
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

    /**
     * 解析模型返回的决策 JSON，并补齐工具输入中的确定性参数。
     *
     * @param content 模型原始响应
     * @param question 用户本轮问题
     * @param topK    本地检索片段数量配置
     * @return 规范化后的智能体决策
     * @throws Exception JSON 解析或字段转换失败时抛出
     */
    private AgentDecision parseDecision(String content, String question, LiteratureSearchContext lastLiteratureContext, Integer topK) throws Exception {
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
        applyDeterministicLiteratureHints(action, input, question, lastLiteratureContext);
        applyTopK(action, input, topK);
        String answer = text(root, "answer", null);
        return new AgentDecision(thought, action, input, finish || action == AgentActionType.FINISH, answer);
    }

    /**
     * 在模型规划失败时使用规则生成下一步决策，已有观察则直接结束。
     *
     * @param question     用户本轮问题
     * @param observations 已收集工具观察
     * @param topK         本地检索片段数量配置
     * @return 兜底智能体决策
     */
    private AgentDecision fallbackDecision(String question,
                                           List<String> observations,
                                           LiteratureSearchContext lastLiteratureContext,
                                           Integer topK) {
        if (observations != null && !observations.isEmpty()) {
            return AgentDecision.finish("已有工具观察，直接整理当前结果。", fallbackAnswerFromObservations(observations));
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("query", question);
        if (looksLikeLiteratureSearch(question) || looksLikeLiteratureFollowUp(question, lastLiteratureContext)) {
            applyDeterministicLiteratureHints(AgentActionType.LITERATURE_SEARCH, input, question, lastLiteratureContext);
            input.putIfAbsent("limit", 5);
            input.remove("topK");
            return new AgentDecision("这是文献搜索类目标，我会先搜索外部论文。", AgentActionType.LITERATURE_SEARCH, input, false, null);
        }
        applyTopK(AgentActionType.LOCAL_PAPER_RETRIEVAL, input, topK);
        return new AgentDecision("这是本地论文分析类目标，我会先检索知识库。", AgentActionType.LOCAL_PAPER_RETRIEVAL, input, false, null);
    }

    /**
     * 在最终回答模型不可用时，将已有观察整理为可读的兜底回答。
     *
     * @param observations 工具观察证据
     * @return 兜底回答文本
     */
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

    /**
     * 截断长文本并保留安全摘要，避免兜底回答过长。
     *
     * @param content   待截断文本
     * @param maxLength 最大长度
     * @return 截断后的文本
     */
    private String cut(String content, int maxLength) {
        return LogSanitizer.safeExcerpt(content, maxLength);
    }

    /**
     * 计算列表大小，空列表按 0 处理。
     *
     * @param items 待统计列表
     * @return 列表大小
     */
    private int size(List<?> items) {
        return items == null ? 0 : items.size();
    }

    /**
     * 计算文本长度，空文本按 0 处理。
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

    /**
     * 按动作类型处理 topK 参数，本地检索保留，外部文献搜索移除。
     *
     * @param action 当前动作类型
     * @param input  工具输入参数
     * @param topK   本地检索片段数量配置
     */
    private void applyTopK(AgentActionType action, Map<String, Object> input, Integer topK) {
        if (action == AgentActionType.LOCAL_PAPER_RETRIEVAL) {
            if (topK != null) {
                input.put("topK", topK);
            }
            return;
        }
        input.remove("topK");
    }

    /**
     * 为外部文献搜索补充可确定的查询词、数量和排序提示。
     *
     * @param action   当前动作类型
     * @param input    工具输入参数
     * @param question 用户本轮问题
     */
    private void applyDeterministicLiteratureHints(AgentActionType action,
                                                   Map<String, Object> input,
                                                   String question,
                                                   LiteratureSearchContext lastLiteratureContext) {
        if (action != AgentActionType.LITERATURE_SEARCH) {
            return;
        }
        LiteratureSearchIntentParser.Intent intent = intentParser.parse(question, lastLiteratureContext);
        if (intent.query() != null && !intent.query().isBlank()) {
            input.put("query", intent.query());
        }
        if (intent.limit() != null) {
            input.put("limit", intent.limit());
        }
        if (intent.sortBy() != null && !intent.sortBy().isBlank()) {
            input.put("sortBy", intent.sortBy());
        }
        if (intent.dateFrom() != null && !intent.dateFrom().isBlank()) {
            input.put("dateFrom", intent.dateFrom());
        }
        if (intent.dateTo() != null && !intent.dateTo().isBlank()) {
            input.put("dateTo", intent.dateTo());
        }
        if (intent.categories() != null && !intent.categories().isEmpty()) {
            input.put("categories", intent.categories());
        } else {
            input.putIfAbsent("categories", List.of());
        }
    }

    /**
     * 基于关键词判断用户目标是否更像外部文献搜索。
     *
     * @param question 用户本轮问题
     * @return 命中文献搜索意图时返回 true
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

    private boolean looksLikeLiteratureFollowUp(String question, LiteratureSearchContext lastLiteratureContext) {
        if (lastLiteratureContext == null || question == null) {
            return false;
        }
        LiteratureSearchIntentParser.Intent intent = intentParser.parse(question, lastLiteratureContext);
        return intent.followUp();
    }

    private AgentDecision finishFromPreviousLiteratureItems(String question,
                                                           LiteratureSearchContext lastLiteratureContext,
                                                           List<String> observations) {
        if (lastLiteratureContext == null || observations != null && !observations.isEmpty()) {
            return null;
        }
        LiteratureSearchIntentParser.Intent intent = intentParser.parse(question, lastLiteratureContext);
        if (!intent.withinPreviousItems() || intent.dateFrom() == null || intent.dateTo() == null) {
            return null;
        }
        List<LiteratureSearchResult> matches = filterPreviousItems(lastLiteratureContext.items(), intent.dateFrom(), intent.dateTo());
        if (matches.isEmpty()) {
            return null;
        }
        String answer = "上一轮文献结果中有 " + matches.size() + " 篇符合条件：\n" + formatMatchedLiterature(matches);
        return AgentDecision.finish("上一轮文献结果中已有可回答的筛选结果。", answer);
    }

    private List<LiteratureSearchResult> filterPreviousItems(List<LiteratureSearchResult> items, String dateFrom, String dateTo) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        LocalDate from = LocalDate.parse(dateFrom);
        LocalDate to = LocalDate.parse(dateTo);
        return items.stream()
                .filter(item -> withinDateRange(item, from, to))
                .toList();
    }

    private boolean withinDateRange(LiteratureSearchResult item, LocalDate from, LocalDate to) {
        if (item == null) {
            return false;
        }
        if (item.year() != null && item.year() >= from.getYear() && item.year() <= to.getYear()) {
            return true;
        }
        if (item.publishedDate() == null || item.publishedDate().isBlank()) {
            return false;
        }
        try {
            LocalDate publishedDate = LocalDate.parse(item.publishedDate().trim());
            return !publishedDate.isBefore(from) && !publishedDate.isAfter(to);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private String formatMatchedLiterature(List<LiteratureSearchResult> items) {
        return items.stream()
                .map(item -> {
                    String title = item.title() == null || item.title().isBlank() ? "未命名文献" : item.title().trim();
                    String year = item.year() == null ? "年份未知" : String.valueOf(item.year());
                    String link = firstNonBlank(item.url(), item.doi(), item.externalId());
                    return link == null || link.isBlank()
                            ? "- " + title + "（" + year + "）"
                            : "- [" + title + "](" + link + ")（" + year + "）";
                })
                .reduce((left, right) -> left + "\n" + right)
                .orElse("未找到匹配文献。");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * 将最近会话历史格式化为提示词片段。
     *
     * @param history 最近会话历史
     * @return 可放入提示词的历史文本
     */
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

    /**
     * 将步骤轨迹格式化为提示词片段，用于让模型理解已有执行过程。
     *
     * @param steps 已执行步骤轨迹
     * @return 可放入提示词的步骤文本
     */
    private String formatSteps(List<AgentStepTrace> steps) {
        if (steps == null || steps.isEmpty()) {
            return "(尚未执行)";
        }
        return steps.stream()
                .map(step -> step.index() + ". " + step.thoughtSummary() + " -> " + step.action() + " -> " + step.observationSummary())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("(尚未执行)");
    }

    /**
     * 将工具观察列表格式化为提示词证据片段。
     *
     * @param observations 工具观察证据
     * @return 可放入提示词的观察文本
     */
    private String formatObservations(List<String> observations) {
        if (observations == null || observations.isEmpty()) {
            return "(暂无观察)";
        }
        return String.join("\n\n", observations);
    }

    /**
     * 从 JSON 节点中读取文本字段，缺失或空白时使用兜底值。
     *
     * @param root     JSON 根节点
     * @param field    字段名称
     * @param fallback 兜底文本
     * @return 规范化后的字段文本
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
     * 从模型响应中提取 JSON 对象部分，兼容模型附带额外文本的情况。
     *
     * @param content 模型原始响应
     * @return JSON 对象字符串或原始响应
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