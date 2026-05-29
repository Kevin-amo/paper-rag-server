package com.lqr.paperragserver.agent.dto;

import com.lqr.paperragserver.common.model.AnswerCitation;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 智能体流式响应事件，描述一次问答过程中的开始、步骤、思考摘要、工具调用、增量回答和结束状态。
 */
public record AgentStreamEvent(
        String type,
        UUID conversationId,
        Integer step,
        String thought,
        String toolName,
        Map<String, Object> toolInput,
        String observation,
        String delta,
        String answer,
        List<AnswerCitation> citations,
        Map<String, Object> metadata,
        String message
) {
    /**
     * 规范化流式事件的集合字段，确保下游序列化和消费端不会收到空集合引用。
     */
    public AgentStreamEvent {
        citations = citations == null ? List.of() : citations;
        metadata = metadata == null ? Map.of() : metadata;
        toolInput = toolInput == null ? Map.of() : toolInput;
    }

    /**
     * 创建一次智能体流式响应的开始事件。
     *
     * @param conversationId 当前会话标识
     * @return 开始事件
     */
    public static AgentStreamEvent start(UUID conversationId) {
        return new AgentStreamEvent("start", conversationId, null, null, null, Map.of(), null, null, null, List.of(), Map.of(), null);
    }

    /**
     * 创建智能体进入指定执行步骤的事件。
     *
     * @param conversationId 当前会话标识
     * @param step           步骤序号
     * @return 步骤事件
     */
    public static AgentStreamEvent step(UUID conversationId, int step) {
        return new AgentStreamEvent("step", conversationId, step, null, null, Map.of(), null, null, null, List.of(), Map.of(), null);
    }

    /**
     * 创建可展示的思考摘要事件。
     *
     * @param conversationId 当前会话标识
     * @param step           步骤序号
     * @param thought        面向用户展示的简短思考摘要
     * @return 思考摘要事件
     */
    public static AgentStreamEvent thought(UUID conversationId, int step, String thought) {
        return new AgentStreamEvent("thought", conversationId, step, thought, null, Map.of(), null, null, null, List.of(), Map.of(), null);
    }

    /**
     * 创建工具调用事件，用于向前端暴露本步骤即将执行的工具和参数。
     *
     * @param conversationId 当前会话标识
     * @param step           步骤序号
     * @param toolName       工具名称
     * @param toolInput      工具输入参数
     * @return 工具调用事件
     */
    public static AgentStreamEvent toolCall(UUID conversationId, int step, String toolName, Map<String, Object> toolInput) {
        return new AgentStreamEvent("tool_call", conversationId, step, null, toolName, toolInput, null, null, null, List.of(), Map.of(), null);
    }

    /**
     * 创建工具执行结果事件，用于返回本步骤的观察摘要。
     *
     * @param conversationId 当前会话标识
     * @param step           步骤序号
     * @param toolName       工具名称
     * @param observation    工具观察摘要
     * @return 工具结果事件
     */
    public static AgentStreamEvent toolResult(UUID conversationId, int step, String toolName, String observation) {
        return new AgentStreamEvent("tool_result", conversationId, step, null, toolName, Map.of(), observation, null, null, List.of(), Map.of(), null);
    }

    /**
     * 创建最终回答的增量片段事件。
     *
     * @param conversationId 当前会话标识
     * @param delta          本次追加的回答文本
     * @return 回答增量事件
     */
    public static AgentStreamEvent delta(UUID conversationId, String delta) {
        return new AgentStreamEvent("delta", conversationId, null, null, null, Map.of(), null, delta, null, List.of(), Map.of(), null);
    }

    /**
     * 创建智能体完成事件，携带最终回答、引用和执行元数据。
     *
     * @param conversationId 当前会话标识
     * @param answer         最终回答文本
     * @param citations      回答引用列表
     * @param metadata       执行过程元数据
     * @return 完成事件
     */
    public static AgentStreamEvent done(UUID conversationId, String answer, List<AnswerCitation> citations, Map<String, Object> metadata) {
        return new AgentStreamEvent("done", conversationId, null, null, null, Map.of(), null, null, answer, citations, metadata, null);
    }

    /**
     * 创建智能体执行失败事件，返回可展示的错误信息。
     *
     * @param conversationId 当前会话标识；可能为空
     * @param message        错误提示文本
     * @return 错误事件
     */
    public static AgentStreamEvent error(UUID conversationId, String message) {
        return new AgentStreamEvent("error", conversationId, null, null, null, Map.of(), null, null, null, List.of(), Map.of(), message);
    }
}