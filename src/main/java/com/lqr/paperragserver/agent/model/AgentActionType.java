package com.lqr.paperragserver.agent.model;

public enum AgentActionType {
    LOCAL_PAPER_RETRIEVAL("local_paper_retrieval"),
    LITERATURE_SEARCH("literature_search"),
    FINISH("finish");

    private final String value;

    AgentActionType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

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