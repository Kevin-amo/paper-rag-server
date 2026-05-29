package com.lqr.paperragserver.agent.tool;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 智能体工具注册表，按工具名称统一管理可被规划器调用的能力。
 */
@Component
public class AgentToolRegistry {

    private final Map<String, AgentTool> tools;

    /**
     * 根据 Spring 注入的工具列表构建只读注册表。
     *
     * @param tools 当前应用启用的智能体工具集合
     */
    public AgentToolRegistry(List<AgentTool> tools) {
        this.tools = tools.stream().collect(Collectors.toUnmodifiableMap(AgentTool::name, tool -> tool));
    }

    /**
     * 按工具名称查找已注册工具，空名称或未知名称返回空结果。
     *
     * @param name 工具名称
     * @return 匹配的工具
     */
    public Optional<AgentTool> find(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(tools.get(name.trim()));
    }

    /**
     * 汇总所有工具的名称和能力描述，供规划提示词使用。
     *
     * @return 多行工具描述文本
     */
    public String toolDescriptions() {
        return tools.values().stream()
                .map(tool -> "- " + tool.name() + ": " + tool.description())
                .collect(Collectors.joining("\n"));
    }
}