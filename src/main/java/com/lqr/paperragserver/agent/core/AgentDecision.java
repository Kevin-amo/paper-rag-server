package com.lqr.paperragserver.agent.core;

import java.util.Map;

public record AgentDecision(
        String thoughtSummary,
        AgentActionType action,
        Map<String, Object> actionInput,
        boolean finish,
        String answer
) {
    public AgentDecision {
        actionInput = actionInput == null ? Map.of() : actionInput;
        action = action == null ? AgentActionType.FINISH : action;
    }

    public static AgentDecision finish(String thoughtSummary, String answer) {
        return new AgentDecision(thoughtSummary, AgentActionType.FINISH, Map.of(), true, answer);
    }
}