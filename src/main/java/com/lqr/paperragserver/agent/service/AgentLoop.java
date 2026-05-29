package com.lqr.paperragserver.agent.service;

import com.lqr.paperragserver.agent.model.AgentActionType;
import com.lqr.paperragserver.agent.model.AgentDecision;
import com.lqr.paperragserver.agent.model.AgentStepTrace;
import com.lqr.paperragserver.agent.dto.AgentStreamEvent;
import com.lqr.paperragserver.agent.model.AgentToolResult;
import com.lqr.paperragserver.agent.tool.AgentTool;
import com.lqr.paperragserver.agent.tool.AgentToolRegistry;
import com.lqr.paperragserver.common.logging.LogSanitizer;
import com.lqr.paperragserver.common.model.AnswerCitation;
import com.lqr.paperragserver.rag.config.RagProperties;
import com.lqr.paperragserver.conversation.service.ConversationService;
import com.lqr.paperragserver.literature.model.LiteratureSearchContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 论文智能体的执行循环，负责按规划决策调用工具、收集证据、归并引用并生成执行轨迹。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentLoop {

    // 定义单步执行最大次数
    private static final int MAX_STEPS = 5;

    private final AgentPlanner planner;
    private final AgentToolRegistry toolRegistry;
    private final RagProperties ragProperties;

    /**
     * 执行一次智能体工具循环，按规划结果选择工具、收集证据并形成可持久化轨迹。
     *
     * @param ownerUserId    当前用户标识
     * @param conversationId 当前会话标识
     * @param question       用户本轮问题
     * @param topK           本地检索片段数量配置
     * @param history        最近会话历史
     * @param lastLiteratureContext 最近一次外部文献搜索上下文
     * @param sink           流式事件消费者
     * @return 智能体循环结果快照
     */
    public AgentLoopResult run(UUID ownerUserId,
                               UUID conversationId,
                               String question,
                               Integer topK,
                               List<ConversationService.MessageView> history,
                               LiteratureSearchContext lastLiteratureContext,
                               Consumer<AgentStreamEvent> sink) {
        List<AgentStepTrace> steps = new ArrayList<>();
        List<String> observations = new ArrayList<>();
        List<AnswerCitation> citations = new ArrayList<>();
        Map<String, Object> extraMetadata = new LinkedHashMap<>();

        log.info("agent.loop.start ownerUserId={} conversationId={} questionLength={} questionExcerpt={} topK={} historyCount={} hasLiteratureContext={} maxSteps={}",
                ownerUserId, conversationId, textLength(question), LogSanitizer.safeExcerpt(question, 160), topK, size(history), lastLiteratureContext != null, MAX_STEPS);
        for (int index = 1; index <= MAX_STEPS; index++) {
            sink.accept(AgentStreamEvent.step(conversationId, index));
            AgentDecision decision = planner.decide(question, history, lastLiteratureContext, steps, observations, topK);
            String thoughtSummary = deterministicThoughtSummary(decision.action());
            log.info("agent.plan.step conversationId={} step={} action={} finish={} actionInputSummary={}",
                    conversationId, index, decision.action(), decision.finish(), LogSanitizer.safeActionInput(decision.actionInput()));

            if (decision.finish()) {
                log.info("agent.loop.done ownerUserId={} conversationId={} stopReason=FINISH stepCount={} citationRawCount={} observationCount={}",
                        ownerUserId, conversationId, steps.size(), citations.size(), observations.size());
                return result(decision.answer(), citations, topK, metadata(steps, extraMetadata), steps, observations);
            }

            AgentTool tool = toolRegistry.find(decision.action().value()).orElse(null);
            if (tool == null || decision.action() == AgentActionType.FINISH) {
                log.warn("agent.loop.stop ownerUserId={} conversationId={} step={} stopReason=NO_TOOL action={}",
                        ownerUserId, conversationId, index, decision.action());
                return result(decision.answer(), citations, topK, metadata(steps, extraMetadata), steps, observations);
            }
            if (hasRepeatedAction(steps, tool.name(), decision.actionInput()) && !observations.isEmpty()) {
                Map<String, Object> metadata = metadata(steps, extraMetadata);
                metadata.put("stopReason", "REPEATED_ACTION");
                log.warn("agent.loop.stop ownerUserId={} conversationId={} step={} stopReason=REPEATED_ACTION toolName={} actionInputSummary={}",
                        ownerUserId, conversationId, index, tool.name(), LogSanitizer.safeActionInput(decision.actionInput()));
                return result(null, citations, topK, metadata, steps, observations);
            }

            sink.accept(AgentStreamEvent.thought(conversationId, index, thoughtSummary));
            sink.accept(AgentStreamEvent.toolCall(conversationId, index, tool.name(), decision.actionInput()));
            long toolStartNanos = System.nanoTime();
            log.info("agent.tool.start ownerUserId={} conversationId={} step={} toolName={} actionInputSummary={}",
                    ownerUserId, conversationId, index, tool.name(), LogSanitizer.safeActionInput(decision.actionInput()));
            AgentToolResult result = executeTool(ownerUserId, tool, decision.actionInput());
            log.info("agent.tool.done ownerUserId={} conversationId={} step={} toolName={} observationLength={} citationCount={} metadataKeys={} costMs={}",
                    ownerUserId, conversationId, index, tool.name(), textLength(result.observationSummary()), result.citations().size(), metadataKeys(result.metadata()), elapsedMs(toolStartNanos));
            citations.addAll(result.citations());
            observations.add(result.evidenceText());
            mergeMetadata(extraMetadata, result.metadata());
            AgentStepTrace trace = new AgentStepTrace(
                    index,
                    thoughtSummary,
                    tool.name(),
                    decision.actionInput(),
                    result.observationSummary()
            );
            steps.add(trace);
            sink.accept(AgentStreamEvent.toolResult(conversationId, index, tool.name(), result.observationSummary()));
            if (isToolUnavailable(result.metadata())) {
                Map<String, Object> metadata = metadata(steps, extraMetadata);
                metadata.put("stopReason", "TOOL_UNAVAILABLE");
                log.warn("agent.loop.stop ownerUserId={} conversationId={} step={} stopReason=TOOL_UNAVAILABLE toolName={}",
                        ownerUserId, conversationId, index, tool.name());
                return result(null, citations, topK, metadata, steps, observations);
            }
        }

        Map<String, Object> metadata = metadata(steps, extraMetadata);
        metadata.put("stopReason", "MAX_STEPS_REACHED");
        log.warn("agent.loop.stop ownerUserId={} conversationId={} stopReason=MAX_STEPS_REACHED stepCount={} citationRawCount={} observationCount={}",
                ownerUserId, conversationId, steps.size(), citations.size(), observations.size());
        return result(null, citations, topK, metadata, steps, observations);
    }

    /**
     * 基于步骤轨迹和工具扩展信息生成当前循环的元数据副本。
     *
     * @param steps         已执行步骤
     * @param extraMetadata 工具扩展元数据
     * @return 可继续补充的元数据映射
     */
    private Map<String, Object> metadata(List<AgentStepTrace> steps, Map<String, Object> extraMetadata) {
        return new LinkedHashMap<>(AgentStepTrace.metadata(steps, extraMetadata));
    }

    /**
     * 统一构造循环结果，并在返回前完成引用去重和截断。
     *
     * @param draftAnswer 规划器给出的回答草稿
     * @param citations   原始引用列表
     * @param topK        本地检索片段数量配置
     * @param metadata    执行元数据
     * @param steps       步骤轨迹
     * @param observations 工具观察证据
     * @return 循环结果快照
     */
    private AgentLoopResult result(String draftAnswer,
                                   List<AnswerCitation> citations,
                                   Integer topK,
                                   Map<String, Object> metadata,
                                   List<AgentStepTrace> steps,
                                   List<String> observations) {
        return new AgentLoopResult(draftAnswer, normalizeCitations(citations, topK), metadata, steps, observations);
    }

    /**
     * 对工具返回的引用进行空值过滤、来源去重、相关度排序和数量限制。
     *
     * @param citations 原始引用列表
     * @param topK      本地检索片段数量配置
     * @return 可展示的引用列表
     */
    private List<AnswerCitation> normalizeCitations(List<AnswerCitation> citations, Integer topK) {
        if (citations == null || citations.isEmpty()) {
            log.info("citation.normalize.done rawCount={} dedupCount={} citationLimit={} finalCount={} duplicateDroppedCount={} truncatedCount={}",
                    0, 0, citationLimit(topK), 0, 0, 0);
            return List.of();
        }
        int rawCount = citations.size();
        Map<String, AnswerCitation> citationByKey = new LinkedHashMap<>();
        int duplicateDroppedCount = 0;
        for (AnswerCitation citation : citations) {
            if (citation == null) {
                continue;
            }
            String key = citationKey(citation);
            AnswerCitation existing = citationByKey.get(key);
            if (existing == null || citation.rankScore() > existing.rankScore()) {
                if (existing != null) {
                    duplicateDroppedCount++;
                }
                citationByKey.put(key, citation);
            } else {
                duplicateDroppedCount++;
            }
        }
        int limit = citationLimit(topK);
        List<AnswerCitation> finalCitations = citationByKey.values().stream()
                .sorted((left, right) -> Double.compare(right.rankScore(), left.rankScore()))
                .limit(limit)
                .toList();
        int truncatedCount = Math.max(0, citationByKey.size() - finalCitations.size());
        log.info("citation.normalize.done rawCount={} dedupCount={} citationLimit={} finalCount={} duplicateDroppedCount={} truncatedCount={}",
                rawCount, citationByKey.size(), limit, finalCitations.size(), duplicateDroppedCount, truncatedCount);
        logDebugCitations(finalCitations);
        return finalCitations;
    }

    /**
     * 计算最终可返回的引用数量上限，优先使用请求配置，否则使用 RAG 默认值。
     *
     * @param topK 请求指定的引用数量配置
     * @return 引用数量上限
     */
    private int citationLimit(Integer topK) {
        if (topK != null && topK > 0) {
            return topK;
        }
        return Math.max(1, ragProperties.defaultTopK());
    }

    /**
     * 在调试日志开启时输出最终引用明细，便于排查引用排序和去重结果。
     *
     * @param citations 最终引用列表
     */
    private void logDebugCitations(List<AnswerCitation> citations) {
        if (!log.isDebugEnabled()) {
            return;
        }
        for (AnswerCitation citation : citations) {
            log.debug("citation.final sourceId={} chunkId={} chunkIndex={} rankScore={} excerpt={}",
                    citation.sourceId(),
                    citation.chunkId(),
                    citation.chunkIndex(),
                    citation.rankScore(),
                    LogSanitizer.safeExcerpt(citation.excerpt(), 160));
        }
    }

    /**
     * 生成引用去重键，优先使用 chunkId，缺失时退化为来源和片段序号组合。
     *
     * @param citation 待归一化的引用
     * @return 引用去重键
     */
    private String citationKey(AnswerCitation citation) {
        if (hasText(citation.chunkId())) {
            return "chunk:" + citation.chunkId();
        }
        return "source-chunk:" + nullToEmpty(citation.sourceId()) + ':' + citation.chunkIndex();
    }

    /**
     * 判断文本是否包含非空白内容。
     *
     * @param value 待判断文本
     * @return 有内容时返回 true
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 将空文本转换为空字符串，避免拼接去重键时出现空引用。
     *
     * @param value 待转换文本
     * @return 非空文本或空字符串
     */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 执行智能体工具，并将工具异常转换为可继续传递的不可用结果。
     *
     * @param ownerUserId 当前用户标识
     * @param tool        待执行工具
     * @param actionInput 工具输入参数
     * @return 工具执行结果或兜底失败结果
     */
    private AgentToolResult executeTool(UUID ownerUserId, AgentTool tool, Map<String, Object> actionInput) {
        try {
            return tool.execute(ownerUserId, actionInput);
        } catch (RuntimeException ex) {
            log.warn("agent.tool.failed ownerUserId={} toolName={} actionInputSummary={}",
                    ownerUserId, tool.name(), LogSanitizer.safeActionInput(actionInput), ex);
            String message = userSafeMessage(ex);
            return new AgentToolResult(
                    tool.name() + " 执行失败：" + message,
                    tool.name() + " 工具暂不可用，未能获取新的检索结果。原因：" + message,
                    List.of(),
                    Map.of("toolUnavailable", true, "toolErrorMessage", message)
            );
        }
    }

    /**
     * 判断工具结果是否表示底层能力不可用。
     *
     * @param metadata 工具返回的元数据
     * @return 不可用时返回 true
     */
    private boolean isToolUnavailable(Map<String, Object> metadata) {
        return metadata != null && Boolean.TRUE.equals(metadata.get("toolUnavailable"));
    }

    /**
     * 提取可面向用户展示的异常信息，缺失时使用通用降级文案。
     *
     * @param ex 工具执行异常
     * @return 用户安全错误信息
     */
    private String userSafeMessage(RuntimeException ex) {
        if (ex == null || ex.getMessage() == null || ex.getMessage().isBlank()) {
            return "服务暂不可用，请稍后重试";
        }
        return ex.getMessage().trim();
    }

    /**
     * 判断当前动作是否与历史步骤重复，避免工具循环在同一输入上反复执行。
     *
     * @param steps       已执行步骤
     * @param action      当前工具或动作名称
     * @param actionInput 当前动作输入
     * @return 已重复时返回 true
     */
    private boolean hasRepeatedAction(List<AgentStepTrace> steps, String action, Map<String, Object> actionInput) {
        if (steps == null || steps.isEmpty()) {
            return false;
        }
        AgentStepTrace lastStep = steps.get(steps.size() - 1);
        if (action.equals(lastStep.action())) {
            return true;
        }
        return steps.stream().anyMatch(step -> action.equals(step.action()) && step.actionInput().equals(actionInput));
    }

    /**
     * 将工具元数据合并到循环级元数据中，空元数据不参与合并。
     *
     * @param target 循环级元数据
     * @param source 工具级元数据
     */
    private void mergeMetadata(Map<String, Object> target, Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        target.putAll(source);
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
     * 计算列表大小，空列表按 0 处理。
     *
     * @param items 待统计列表
     * @return 列表大小
     */
    private int size(List<?> items) {
        return items == null ? 0 : items.size();
    }

    /**
     * 提取元数据键集合并按字典序排列，便于日志稳定输出。
     *
     * @param metadata 元数据映射
     * @return 排序后的元数据键集合
     */
    private java.util.Set<String> metadataKeys(Map<String, Object> metadata) {
        return metadata == null ? java.util.Set.of() : new java.util.TreeSet<>(metadata.keySet());
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
     * 根据动作类型生成可展示的确定性思考摘要，避免暴露模型隐私思维链。
     *
     * @param action 当前动作类型
     * @return 面向用户展示的简短摘要
     */
    private String deterministicThoughtSummary(AgentActionType action) {
        return switch (action) {
            case LITERATURE_SEARCH -> "用户需要搜索外部文献，我将调用外部文献搜索。";
            case LOCAL_PAPER_RETRIEVAL -> "用户需要分析已上传文档，我将检索本地论文知识库。";
            case FINISH -> "已有工具结果足够回答，我将整理最终回复。";
        };
    }

    /**
     * 智能体单轮执行完成后的结果快照，包含回答草稿、引用、元数据、步骤轨迹和工具观察。
     *
     * @param draftAnswer  规划阶段产生的回答草稿
     * @param citations    归一化后的引用列表
     * @param metadata     可持久化的执行元数据
     * @param steps        步骤轨迹
     * @param observations 工具观察证据
     */
    public record AgentLoopResult(
            String draftAnswer,
            List<AnswerCitation> citations,
            Map<String, Object> metadata,
            List<AgentStepTrace> steps,
            List<String> observations
    ) {
        /**
         * 固化循环结果中的集合字段，确保快照不可变且不包含空集合引用。
         */
        public AgentLoopResult {
            citations = citations == null ? List.of() : List.copyOf(citations);
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
            steps = steps == null ? List.of() : List.copyOf(steps);
            observations = observations == null ? List.of() : List.copyOf(observations);
        }
    }
}
