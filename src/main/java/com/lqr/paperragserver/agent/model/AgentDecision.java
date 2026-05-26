package com.lqr.paperragserver.agent.model;

import java.util.Map;

/**
 * 智能体规划器输出的下一步决策，包含动作类型、动作输入、结束标记和回答草稿。
 */
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