package com.lqr.paperragserver.agent.tool;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class AgentToolRegistry {

    private final Map<String, AgentTool> tools;

    public AgentToolRegistry(List<AgentTool> tools) {
        this.tools = tools.stream().collect(Collectors.toUnmodifiableMap(AgentTool::name, tool -> tool));
    }

    public Optional<AgentTool> find(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(tools.get(name.trim()));
    }

    public String toolDescriptions() {
        return tools.values().stream()
                .map(tool -> "- " + tool.name() + ": " + tool.description())
                .collect(Collectors.joining("\n"));
    }
}