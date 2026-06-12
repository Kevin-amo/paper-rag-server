package com.lqr.papermind.conversation.web;

import com.lqr.papermind.auth.security.SecurityUserPrincipal;
import com.lqr.papermind.conversation.dto.CreateConversationRequest;
import com.lqr.papermind.conversation.dto.UpdateConversationRequest;
import com.lqr.papermind.conversation.service.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 会话接口入口，负责把当前登录用户的会话请求转发到会话服务。
 */
@RestController
@RequestMapping("/conversations")
@RequiredArgsConstructor
public class ConversationController {

    /** 会话领域服务 */
    private final ConversationService conversationService;

    /**
     * 获取当前用户的会话列表。
     *
     * @param principal 当前登录用户
     * @return 当前用户可见的会话列表
     */
    @GetMapping
    public List<ConversationService.ConversationView> list(@AuthenticationPrincipal SecurityUserPrincipal principal) {
        return conversationService.listConversations(principal.getId());
    }

    /**
     * 为当前用户创建新会话。
     *
     * @param principal 当前登录用户
     * @param request 创建会话请求
     * @return 创建后的会话视图
     */
    @PostMapping
    public ConversationService.ConversationView create(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                       @Valid @RequestBody CreateConversationRequest request) {
        return conversationService.createConversation(principal.getId(), request.title());
    }

    /**
     * 修改当前用户指定会话的标题。
     *
     * @param principal 当前登录用户
     * @param conversationId 会话 ID
     * @param request 更新会话请求
     * @return 更新后的会话视图
     */
    @PatchMapping("/{conversationId}")
    public ConversationService.ConversationView update(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                       @PathVariable UUID conversationId,
                                                       @Valid @RequestBody UpdateConversationRequest request) {
        return conversationService.renameConversation(principal.getId(), conversationId, request.title());
    }

    /**
     * 获取当前用户指定会话下的消息列表。
     *
     * @param principal 当前登录用户
     * @param conversationId 会话 ID
     * @return 会话消息列表
     */
    @GetMapping("/{conversationId}/messages")
    public List<ConversationService.MessageView> messages(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                          @PathVariable UUID conversationId) {
        return conversationService.listMessages(principal.getId(), conversationId);
    }

    /**
     * 删除当前用户指定会话。
     *
     * @param principal 当前登录用户
     * @param conversationId 会话 ID
     */
    @DeleteMapping("/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal SecurityUserPrincipal principal,
                       @PathVariable UUID conversationId) {
        conversationService.deleteConversation(principal.getId(), conversationId);
    }
}