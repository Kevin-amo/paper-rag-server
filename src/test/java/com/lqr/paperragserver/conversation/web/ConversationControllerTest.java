package com.lqr.paperragserver.conversation.web;

import com.lqr.paperragserver.auth.entity.SysUser;
import com.lqr.paperragserver.auth.security.SecurityUserPrincipal;
import com.lqr.paperragserver.common.model.AnswerCitation;
import com.lqr.paperragserver.conversation.dto.CreateConversationRequest;
import com.lqr.paperragserver.conversation.dto.UpdateConversationRequest;
import com.lqr.paperragserver.conversation.service.ConversationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationControllerTest {

    private final ConversationService conversationService = mock(ConversationService.class);
    private ConversationController controller;
    private UUID ownerUserId;
    private SecurityUserPrincipal principal;

    @BeforeEach
    void setUp() {
        controller = new ConversationController(conversationService);
        ownerUserId = UUID.randomUUID();
        principal = principal(ownerUserId);
    }

    @Test
    void listShouldDelegateWithCurrentUserId() {
        ConversationService.ConversationView conversation = conversationView("论文问答");
        when(conversationService.listConversations(ownerUserId)).thenReturn(List.of(conversation));

        List<ConversationService.ConversationView> response = controller.list(principal);

        assertThat(response).containsExactly(conversation);
        verify(conversationService).listConversations(ownerUserId);
    }

    @Test
    void createShouldDelegateWithCurrentUserIdAndTitle() {
        ConversationService.ConversationView conversation = conversationView("新会话");
        when(conversationService.createConversation(ownerUserId, "新会话")).thenReturn(conversation);

        ConversationService.ConversationView response = controller.create(
                principal,
                new CreateConversationRequest("新会话")
        );

        assertThat(response).isEqualTo(conversation);
        verify(conversationService).createConversation(ownerUserId, "新会话");
    }

    @Test
    void updateShouldDelegateWithCurrentUserIdConversationIdAndTitle() {
        UUID conversationId = UUID.randomUUID();
        ConversationService.ConversationView conversation = conversationView("重命名会话");
        when(conversationService.renameConversation(ownerUserId, conversationId, "重命名会话")).thenReturn(conversation);

        ConversationService.ConversationView response = controller.update(
                principal,
                conversationId,
                new UpdateConversationRequest("重命名会话")
        );

        assertThat(response).isEqualTo(conversation);
        verify(conversationService).renameConversation(ownerUserId, conversationId, "重命名会话");
    }

    @Test
    void messagesShouldDelegateWithCurrentUserIdAndConversationId() {
        UUID conversationId = UUID.randomUUID();
        ConversationService.MessageView message = new ConversationService.MessageView(
                UUID.randomUUID(),
                conversationId,
                "ASSISTANT",
                2,
                "答案",
                List.of(new AnswerCitation("source-1", "chunk-1", 0, "标题", "摘录", 0.9)),
                null,
                OffsetDateTime.now()
        );
        when(conversationService.listMessages(ownerUserId, conversationId)).thenReturn(List.of(message));

        List<ConversationService.MessageView> response = controller.messages(principal, conversationId);

        assertThat(response).containsExactly(message);
        verify(conversationService).listMessages(ownerUserId, conversationId);
    }

    @Test
    void deleteShouldDelegateWithCurrentUserIdAndConversationId() {
        UUID conversationId = UUID.randomUUID();

        controller.delete(principal, conversationId);

        verify(conversationService).deleteConversation(ownerUserId, conversationId);
    }

    private ConversationService.ConversationView conversationView(String title) {
        OffsetDateTime now = OffsetDateTime.now();
        return new ConversationService.ConversationView(UUID.randomUUID(), ownerUserId, title, now, now);
    }

    private SecurityUserPrincipal principal(UUID userId) {
        SysUser user = new SysUser();
        user.setId(userId);
        user.setUsername("user");
        user.setPasswordHash("{noop}password");
        user.setDisplayName("User");
        user.setStatus("ACTIVE");
        return new SecurityUserPrincipal(user, List.of("USER"));
    }
}