package com.lqr.paperragserver.agent.tool;

import com.lqr.paperragserver.agent.model.AgentToolResult;

import java.util.Map;
import java.util.UUID;

public interface AgentTool {

    String name();

    String description();

    AgentToolResult execute(UUID ownerUserId, Map<String, Object> input);
}