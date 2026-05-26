package com.lqr.paperragserver.conversation.dto;

import jakarta.validation.constraints.Size;

public record UpdateConversationRequest(@Size(max = 160, message = "会话标题不能超过160个字符") String title) {
}