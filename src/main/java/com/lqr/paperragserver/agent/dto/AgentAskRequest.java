package com.lqr.paperragserver.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/**
 * 智能体问答请求载体，包含目标会话、用户问题和本地检索片段数量配置。
 *
 * @param conversationId 目标会话标识；为空时表示创建新会话
 * @param question       用户本轮提交的问题或任务目标
 * @param topK           本地检索返回片段数量；为空时使用系统默认配置
 */
public record AgentAskRequest(
        UUID conversationId,
        @NotBlank(message = "目标不能为空") String question,
        @Positive(message = "topK 必须为正数") Integer topK
) {
}