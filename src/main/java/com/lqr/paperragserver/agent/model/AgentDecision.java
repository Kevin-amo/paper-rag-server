package com.lqr.paperragserver.agent.model;

import java.util.Map;

/**
 * 智能体规划器输出的下一步决策，包含动作类型、动作输入、结束标记和回答草稿。
 *
 * @param thoughtSummary 可展示的简短思考摘要
 * @param action         下一步动作类型
 * @param actionInput    传递给工具的结构化参数
 * @param finish         是否结束工具循环并进入最终回答
 * @param answer         结束时的回答草稿
 */
public record AgentDecision(
        String thoughtSummary,
        AgentActionType action,
        Map<String, Object> actionInput,
        boolean finish,
        String answer
) {
    /**
     * 规范化决策中的动作和输入参数，避免后续执行阶段处理空值。
     */
    public AgentDecision {
        actionInput = actionInput == null ? Map.of() : actionInput;
        action = action == null ? AgentActionType.FINISH : action;
    }

    /**
     * 创建结束型决策，表示工具循环可停止并进入回答生成阶段。
     *
     * @param thoughtSummary 可展示的结束原因摘要
     * @param answer         回答草稿
     * @return 结束型决策
     */
    public static AgentDecision finish(String thoughtSummary, String answer) {
        return new AgentDecision(thoughtSummary, AgentActionType.FINISH, Map.of(), true, answer);
    }
}