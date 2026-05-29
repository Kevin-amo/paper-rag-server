package com.lqr.paperragserver.agent.model;

import java.util.List;
import java.util.Map;

/**
 * 智能体单步执行轨迹，记录步骤序号、动作、动作输入和工具观察摘要。
 *
 * @param index              步骤序号
 * @param thoughtSummary     可展示的动作选择摘要
 * @param action             实际执行的工具或动作名称
 * @param actionInput        本步骤使用的工具参数
 * @param observationSummary 工具返回的观察摘要
 */
public record AgentStepTrace(
        int index,
        String thoughtSummary,
        String action,
        Map<String, Object> actionInput,
        String observationSummary
) {
    /**
     * 规范化步骤输入参数，确保轨迹序列化时不暴露空集合引用。
     */
    public AgentStepTrace {
        actionInput = actionInput == null ? Map.of() : actionInput;
    }

    /**
     * 组装一次智能体执行结果的统一元数据结构。
     *
     * @param steps         已执行的步骤轨迹
     * @param extraMetadata 工具产生的扩展元数据
     * @return 可持久化到会话消息中的元数据
     */
    public static Map<String, Object> metadata(List<AgentStepTrace> steps, Map<String, Object> extraMetadata) {
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("type", "AGENT_RESULT");
        metadata.put("agent", "paper_super_agent");
        metadata.put("steps", steps == null ? List.of() : steps);
        if (extraMetadata != null && !extraMetadata.isEmpty()) {
            metadata.putAll(extraMetadata);
        }
        return metadata;
    }
}