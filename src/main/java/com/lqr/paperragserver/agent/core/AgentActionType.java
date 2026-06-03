package com.lqr.paperragserver.agent.core;

public enum AgentActionType {
    /** 本地论文知识库检索 */
    LOCAL_PAPER_RETRIEVAL("local_paper_retrieval"),
    /** 外部文献搜索 */
    LITERATURE_SEARCH("literature_search"),
    /** 结束当前决策循环 */
    FINISH("finish");

    private final String value;

    /**
     * 初始化智能体动作类型与规划器使用的动作值。
     *
     * @param value 动作值
     */
    AgentActionType(String value) {
        this.value = value;
    }

    /**
     * 返回规划器、工具注册表和前端事件使用的动作值。
     *
     * @return 动作值
     */
    public String value() {
        return value;
    }

    /**
     * 将模型输出或外部输入转换为受支持的动作类型，无法识别时回退为结束动作。
     *
     * @param value 动作文本
     * @return 智能体动作类型
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