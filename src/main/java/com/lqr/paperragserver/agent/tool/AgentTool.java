package com.lqr.paperragserver.agent.tool;

import java.util.Map;
import java.util.UUID;

/**
 * 智能体可调用工具的统一契约，定义工具名称、能力描述和执行入口。
 */
public interface AgentTool {

    /**
     * 返回规划器和注册表使用的唯一工具名称。
     *
     * @return 工具名称
     */
    String name();

    /**
     * 返回面向规划器的能力说明，用于构造可用工具列表。
     *
     * @return 工具能力描述
     */
    String description();

    /**
     * 使用当前用户上下文和规划器参数执行工具。
     *
     * @param ownerUserId 当前用户标识
     * @param input       规划器生成的工具输入
     * @return 工具执行结果
     */
    AgentToolResult execute(UUID ownerUserId, Map<String, Object> input);
}