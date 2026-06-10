package com.lqr.papermind.agent.core;

import java.util.Map;

public record AgentDecision(
        String thoughtSummary,
        AgentActionType action,
        Map<String, Object> actionInput,
        boolean finish,
        String answer
) {
    /**
     * 规范化智能体决策，确保动作和输入参数始终存在可用默认值。
     */
    public AgentDecision {
        actionInput = actionInput == null ? Map.of() : actionInput;
        action = action == null ? AgentActionType.FINISH : action;
    }

    /**
     * 创建结束型决策，表示当前信息已足够进入最终回答阶段。
     *
     * @param thoughtSummary 面向用户展示的简短思考摘要
     * @param answer         决策器给出的回答草稿
     * @return 结束型智能体决策
     */
    public static AgentDecision finish(String thoughtSummary, String answer) {
        return new AgentDecision(thoughtSummary, AgentActionType.FINISH, Map.of(), true, answer);
    }
}