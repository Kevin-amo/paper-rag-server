package com.lqr.paperragserver.agent.model;

/**
 * 智能体规划阶段支持的动作类型，用于区分本地论文检索、外部文献搜索和结束回答。
 */
public enum AgentActionType {
    LOCAL_PAPER_RETRIEVAL("local_paper_retrieval"),
    LITERATURE_SEARCH("literature_search"),
    FINISH("finish");

    private final String value;

    /**
     * 绑定前端和规划器共同使用的动作标识。
     *
     * @param value 对外传输的动作名称
     */
    AgentActionType(String value) {
        this.value = value;
    }

    /**
     * 获取对外传输的动作名称。
     *
     * @return 动作名称
     */
    public String value() {
        return value;
    }

    /**
     * 将模型输出或外部输入转换为受支持的动作类型；无法识别时回退为结束动作。
     *
     * @param value 动作名称或枚举名称
     * @return 规范化后的动作类型
     */
    public static AgentActionType from(String value) {
        if (value == null || value.isBlank()) {
            return FINISH;
        }
        String normalized = value.trim();
        for (AgentActionType type : values()) {
            if (type.value.equalsIgnoreCase(normalized) || type.name().equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        return FINISH;
    }
}