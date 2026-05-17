package com.lqr.paperragserver.web;

import com.lqr.paperragserver.auth.security.SecurityUserPrincipal;
import com.lqr.paperragserver.conversation.service.ConversationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping
    public List<ConversationService.ConversationView> list(@AuthenticationPrincipal SecurityUserPrincipal principal) {
        return conversationService.listConversations(principal.getId());
    }

    @PostMapping
    public ConversationService.ConversationView create(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                       @Valid @RequestBody CreateConversationRequest request) {
        return conversationService.createConversation(principal.getId(), request.title());
    }

    @GetMapping("/{conversationId}/messages")
    public List<ConversationService.MessageView> messages(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                          @PathVariable UUID conversationId) {
        return conversationService.listMessages(principal.getId(), conversationId);
    }

    @DeleteMapping("/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal SecurityUserPrincipal principal,
                       @PathVariable UUID conversationId) {
        conversationService.deleteConversation(principal.getId(), conversationId);
    }

    public record CreateConversationRequest(@Size(max = 160, message = "会话标题不能超过160个字符") String title) {
    }
}