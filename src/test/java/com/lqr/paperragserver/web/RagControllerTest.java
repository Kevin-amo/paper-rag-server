package com.lqr.paperragserver.web;

import com.lqr.paperragserver.auth.entity.SysUser;
import com.lqr.paperragserver.auth.security.SecurityUserPrincipal;
import com.lqr.paperragserver.common.model.RagAnswer;
import com.lqr.paperragserver.common.model.RagStreamEvent;
import com.lqr.paperragserver.rag.service.RagAnswerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagControllerTest {

    private final RagAnswerService ragAnswerService = mock(RagAnswerService.class);
    private RagController controller;
    private UUID ownerUserId;
    private SecurityUserPrincipal principal;

    @BeforeEach
    void setUp() {
        controller = new RagController(ragAnswerService);
        ownerUserId = UUID.randomUUID();
        principal = principal(ownerUserId);
    }

    @Test
    void askShouldDelegateWithCurrentUserId() {
        UUID conversationId = UUID.randomUUID();
        RagController.AskRequest request = new RagController.AskRequest(conversationId, "question", 3);
        RagAnswer expected = new RagAnswer("answer", List.of(), conversationId);
        when(ragAnswerService.answer(ownerUserId, conversationId, "question", 3)).thenReturn(expected);

        RagAnswer response = controller.ask(principal, request);

        assertThat(response).isEqualTo(expected);
        verify(ragAnswerService).answer(ownerUserId, conversationId, "question", 3);
    }

    @Test
    void askStreamShouldExposeTextEventStreamEndpoint() throws NoSuchMethodException {
        Method method = RagController.class.getMethod(
                "askStream",
                SecurityUserPrincipal.class,
                RagController.AskRequest.class
        );
        PostMapping mapping = method.getAnnotation(PostMapping.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/ask/stream");
        assertThat(mapping.produces()).containsExactly(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    @Test
    void askStreamShouldDelegateWithCurrentUserId() {
        UUID conversationId = UUID.randomUUID();
        RagController.AskRequest request = new RagController.AskRequest(conversationId, "stream question", 4);
        when(ragAnswerService.streamAnswer(ownerUserId, conversationId, "stream question", 4))
                .thenReturn(Flux.just(
                        RagStreamEvent.start(conversationId),
                        RagStreamEvent.delta(conversationId, "answer"),
                        RagStreamEvent.done(conversationId, "answer", List.of())
                ));

        SseEmitter emitter = controller.askStream(principal, request);

        assertThat(emitter).isNotNull();
        verify(ragAnswerService).streamAnswer(ownerUserId, conversationId, "stream question", 4);
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