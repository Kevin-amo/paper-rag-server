package com.lqr.papermind.conversation.dto;

import jakarta.validation.constraints.Size;

/**
 * 更新会话请求，承载用户传入的新会话标题。
 *
 * @param title 新会话标题，最长 160 个字符
 */
public record UpdateConversationRequest(@Size(max = 160, message = "会话标题不能超过160个字符") String title) {
}