package com.lqr.paperragserver.agent.tool;

import com.lqr.paperragserver.agent.model.AgentToolResult;

import java.util.Map;
import java.util.UUID;

/**
 * 智能体可调用工具的统一契约，定义工具名称、能力描述和执行入口。
 */
public interface AgentTool {

    String name();

    String description();

    AgentToolResult execute(UUID ownerUserId, Map<String, Object> input);
}