package com.lqr.papermind.agent.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AgentStep(
        int index,
        String thoughtSummary,
        String action,
        Map<String, Object> actionInput,
        String observationSummary
) {
    /**
     * 规范化步骤输入参数，避免下游消费执行轨迹时遇到空 Map。
     */
    public AgentStep {
        actionInput = actionInput == null ? Map.of() : actionInput;
    }

    /**
     * 组装智能体执行结果元数据，合并步骤轨迹和额外业务元数据。
     *
     * @param steps         智能体执行步骤列表
     * @param extraMetadata 额外业务元数据
     * @return 可持久化到消息记录中的结果元数据
     */
    public static Map<String, Object> metadata(List<AgentStep> steps, Map<String, Object> extraMetadata) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("type", "AGENT_RESULT");
        metadata.put("agent", "paper_super_agent");
        metadata.put("steps", steps == null ? List.of() : steps);
        if (extraMetadata != null && !extraMetadata.isEmpty()) {
            metadata.putAll(extraMetadata);
        }
        return metadata;
    }
}