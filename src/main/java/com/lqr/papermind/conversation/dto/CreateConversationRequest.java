package com.lqr.papermind.conversation.dto;

import jakarta.validation.constraints.Size;

/**
 * 创建会话请求，承载用户传入的可选会话标题。
 *
 * @param title 会话标题，最长 160 个字符
 */
public record CreateConversationRequest(@Size(max = 160, message = "会话标题不能超过160个字符") String title) {
}