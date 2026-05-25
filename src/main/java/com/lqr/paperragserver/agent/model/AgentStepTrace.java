package com.lqr.paperragserver.agent.model;

import java.util.List;
import java.util.Map;

public record AgentStepTrace(
        int index,
        String thoughtSummary,
        String action,
        Map<String, Object> actionInput,
        String observationSummary
) {
    public AgentStepTrace {
        actionInput = actionInput == null ? Map.of() : actionInput;
    }

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