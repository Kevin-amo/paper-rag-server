package com.lqr.paperragserver.agent.core;

import com.lqr.paperragserver.agent.paper.CitationNormalizer;
import com.lqr.paperragserver.agent.planning.AgentHybridTaskPolicy;
import com.lqr.paperragserver.agent.planning.AgentPlanner;
import com.lqr.paperragserver.agent.tool.AgentTool;
import com.lqr.paperragserver.agent.tool.AgentToolRegistry;
import com.lqr.paperragserver.agent.tool.AgentToolResult;
import com.lqr.paperragserver.common.logging.LogSanitizer;
import com.lqr.paperragserver.common.model.AnswerCitation;
import com.lqr.paperragserver.conversation.service.ConversationService;
import com.lqr.paperragserver.literature.model.LiteratureSearchContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRuntime {

    private static final int MAX_STEPS = 5;

    private final AgentPlanner planner;
    private final AgentToolRegistry toolRegistry;
    private final CitationNormalizer citationNormalizer;
    private final AgentHybridTaskPolicy hybridTaskPolicy;

    /**
     * 执行完整 ReAct 循环，按规划决策调用工具并累计步骤、观察、引用和元数据。
     *
     * @param ownerUserId           当前用户标识
     * @param conversationId        当前会话标识
     * @param question              用户当前问题
     * @param topK                  本地检索片段数量配置
     * @param history               最近会话历史
     * @param lastLiteratureContext 最近一次文献搜索上下文
     * @param sink                  运行时事件消费者
     * @return 智能体运行结果
     */
    public AgentRuntimeResult run(UUID ownerUserId,
                                  UUID conversationId,
                                  String question,
                                  Integer topK,
                                  List<ConversationService.MessageView> history,
                                  LiteratureSearchContext lastLiteratureContext,
                                  Consumer<AgentRuntimeEvent> sink) {
        List<AgentStep> steps = new ArrayList<>();
        List<String> observations = new ArrayList<>();
        List<AnswerCitation> citations = new ArrayList<>();
        Map<String, Object> extraMetadata = new LinkedHashMap<>();

        log.info("agent.loop.start ownerUserId={} conversationId={} questionLength={} questionExcerpt={} topK={} historyCount={} hasLiteratureContext={} maxSteps={}",
                ownerUserId, conversationId, textLength(question), LogSanitizer.safeExcerpt(question, 160), topK, size(history), lastLiteratureContext != null, MAX_STEPS);
        for (int index = 1; index <= MAX_STEPS; index++) {
            sink.accept(AgentRuntimeEvent.step(index));
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

            sink.accept(AgentRuntimeEvent.thought(index, thoughtSummary));
            sink.accept(AgentRuntimeEvent.toolCall(index, tool.name(), decision.actionInput()));
            long toolStartNanos = System.nanoTime();
            log.info("agent.tool.start ownerUserId={} conversationId={} step={} toolName={} actionInputSummary={}",
                    ownerUserId, conversationId, index, tool.name(), LogSanitizer.safeActionInput(decision.actionInput()));
            AgentToolResult result = executeTool(ownerUserId, tool, decision.actionInput());
            log.info("agent.tool.done ownerUserId={} conversationId={} step={} toolName={} observationLength={} citationCount={} metadataKeys={} costMs={}",
                    ownerUserId, conversationId, index, tool.name(), textLength(result.observationSummary()), result.citations().size(), metadataKeys(result.metadata()), elapsedMs(toolStartNanos));
            citations.addAll(result.citations());
            observations.add(result.evidenceText());
            AgentStep trace = new AgentStep(
                    index,
                    thoughtSummary,
                    tool.name(),
                    decision.actionInput(),
                    result.observationSummary()
            );
            steps.add(trace);
            sink.accept(AgentRuntimeEvent.toolResult(index, tool.name(), result.observationSummary()));
            if (isToolUnavailable(result.metadata())) {
                if (hybridTaskPolicy.shouldContinueAfterLiteratureUnavailable(question, tool.name(), steps)) {
                    extraMetadata.put("literatureUnavailable", true);
                    Object reason = result.metadata().get("toolErrorMessage");
                    extraMetadata.putIfAbsent("literatureUnavailableReason", reason == null ? "外部文献工具暂不可用" : reason);
                    log.warn("agent.loop.continue ownerUserId={} conversationId={} step={} reason=HYBRID_LITERATURE_UNAVAILABLE toolName={}",
                            ownerUserId, conversationId, index, tool.name());
                    continue;
                }
                mergeMetadata(extraMetadata, result.metadata());
                Map<String, Object> metadata = metadata(steps, extraMetadata);
                metadata.put("stopReason", "TOOL_UNAVAILABLE");
                log.warn("agent.loop.stop ownerUserId={} conversationId={} step={} stopReason=TOOL_UNAVAILABLE toolName={}",
                        ownerUserId, conversationId, index, tool.name());
                return result(null, citations, topK, metadata, steps, observations);
            }
            mergeMetadata(extraMetadata, result.metadata());
        }

        Map<String, Object> metadata = metadata(steps, extraMetadata);
        metadata.put("stopReason", "MAX_STEPS_REACHED");
        log.warn("agent.loop.stop ownerUserId={} conversationId={} stopReason=MAX_STEPS_REACHED stepCount={} citationRawCount={} observationCount={}",
                ownerUserId, conversationId, steps.size(), citations.size(), observations.size());
        return result(null, citations, topK, metadata, steps, observations);
    }

    /**
     * 合并标准执行轨迹元数据和工具返回的额外元数据。
     *
     * @param steps         已执行步骤
     * @param extraMetadata 工具返回的额外元数据
     * @return 智能体结果元数据
     */
    private Map<String, Object> metadata(List<AgentStep> steps, Map<String, Object> extraMetadata) {
        return new LinkedHashMap<>(AgentStep.metadata(steps, extraMetadata));
    }

    /**
     * 创建运行结果，并在返回前完成引用归一化。
     *
     * @param draftAnswer  回答草稿
     * @param citations    原始引用列表
     * @param topK         本地检索片段数量配置
     * @param metadata     结果元数据
     * @param steps        已执行步骤
     * @param observations 工具观察结果
     * @return 智能体运行结果
     */
    private AgentRuntimeResult result(String draftAnswer,
                                      List<AnswerCitation> citations,
                                      Integer topK,
                                      Map<String, Object> metadata,
                                      List<AgentStep> steps,
                                      List<String> observations) {
        return new AgentRuntimeResult(draftAnswer, citationNormalizer.normalize(citations, topK), metadata, steps, observations);
    }

    /**
     * 调用指定工具，并在工具异常时转换为可被后续流程消费的失败结果。
     *
     * @param ownerUserId 当前用户标识
     * @param tool        待调用工具
     * @param actionInput 工具输入参数
     * @return 工具执行结果
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
     * 判断工具结果元数据是否标记为暂不可用。
     *
     * @param metadata 工具结果元数据
     * @return 是否暂不可用
     */
    private boolean isToolUnavailable(Map<String, Object> metadata) {
        return metadata != null && Boolean.TRUE.equals(metadata.get("toolUnavailable"));
    }

    /**
     * 提取可直接展示给用户的工具异常信息。
     *
     * @param ex 工具执行异常
     * @return 用户可读错误信息
     */
    private String userSafeMessage(RuntimeException ex) {
        if (ex == null || ex.getMessage() == null || ex.getMessage().isBlank()) {
            return "服务暂不可用，请稍后重试";
        }
        return ex.getMessage().trim();
    }

    /**
     * 判断当前动作是否与历史步骤重复，避免工具调用在同一输入上循环。
     *
     * @param steps       已执行步骤
     * @param action      当前工具动作
     * @param actionInput 当前工具输入
     * @return 是否重复调用
     */
    private boolean hasRepeatedAction(List<AgentStep> steps, String action, Map<String, Object> actionInput) {
        if (steps == null || steps.isEmpty()) {
            return false;
        }
        AgentStep lastStep = steps.get(steps.size() - 1);
        if (action.equals(lastStep.action())) {
            return true;
        }
        return steps.stream().anyMatch(step -> action.equals(step.action()) && step.actionInput().equals(actionInput));
    }

    /**
     * 将工具元数据合并到本轮累计元数据中。
     *
     * @param target 累计元数据
     * @param source 工具返回元数据
     */
    private void mergeMetadata(Map<String, Object> target, Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        target.putAll(source);
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
     * 计算列表大小，空列表引用按 0 处理。
     *
     * @param items 待统计列表
     * @return 列表大小
     */
    private int size(List<?> items) {
        return items == null ? 0 : items.size();
    }

    /**
     * 提取元数据键集合，用于结构化日志输出。
     *
     * @param metadata 元数据
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
     * 根据动作类型生成稳定的可展示思考摘要，避免暴露模型隐私思维链。
     *
     * @param action 智能体动作类型
     * @return 可展示思考摘要
     */
    private String deterministicThoughtSummary(AgentActionType action) {
        return switch (action) {
            case LITERATURE_SEARCH -> "用户需要搜索外部文献，我将调用外部文献搜索。";
            case LOCAL_PAPER_RETRIEVAL -> "用户需要分析已上传文档，我将检索本地论文知识库。";
            case FINISH -> "已有工具结果足够回答，我将整理最终回复。";
        };
    }

    public record AgentRuntimeResult(
            String draftAnswer,
            List<AnswerCitation> citations,
            Map<String, Object> metadata,
            List<AgentStep> steps,
            List<String> observations
    ) {
        /**
         * 规范化智能体运行结果，确保集合字段对下游只读且非空。
         */
        public AgentRuntimeResult {
            citations = citations == null ? List.of() : List.copyOf(citations);
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
            steps = steps == null ? List.of() : List.copyOf(steps);
            observations = observations == null ? List.of() : List.copyOf(observations);
        }
    }

    public record AgentRuntimeEvent(
            Type type,
            int step,
            String thoughtSummary,
            String toolName,
            Map<String, Object> actionInput,
            String observationSummary
    ) {
        /**
         * 规范化运行时事件输入参数，避免事件消费者收到空 Map。
         */
        public AgentRuntimeEvent {
            actionInput = actionInput == null ? Map.of() : actionInput;
        }

        /**
         * 创建步骤开始事件。
         *
         * @param step 步骤序号
         * @return 步骤开始事件
         */
        static AgentRuntimeEvent step(int step) {
            return new AgentRuntimeEvent(Type.STEP, step, null, null, Map.of(), null);
        }

        /**
         * 创建思考摘要事件。
         *
         * @param step           步骤序号
         * @param thoughtSummary 可展示思考摘要
         * @return 思考摘要事件
         */
        static AgentRuntimeEvent thought(int step, String thoughtSummary) {
            return new AgentRuntimeEvent(Type.THOUGHT, step, thoughtSummary, null, Map.of(), null);
        }

        /**
         * 创建工具调用事件。
         *
         * @param step        步骤序号
         * @param toolName    工具名称
         * @param actionInput 工具输入参数
         * @return 工具调用事件
         */
        static AgentRuntimeEvent toolCall(int step, String toolName, Map<String, Object> actionInput) {
            return new AgentRuntimeEvent(Type.TOOL_CALL, step, null, toolName, actionInput, null);
        }

        /**
         * 创建工具结果事件。
         *
         * @param step               步骤序号
         * @param toolName           工具名称
         * @param observationSummary 工具观察摘要
         * @return 工具结果事件
         */
        static AgentRuntimeEvent toolResult(int step, String toolName, String observationSummary) {
            return new AgentRuntimeEvent(Type.TOOL_RESULT, step, null, toolName, Map.of(), observationSummary);
        }

        public enum Type {
            STEP,
            THOUGHT,
            TOOL_CALL,
            TOOL_RESULT
        }
    }
}