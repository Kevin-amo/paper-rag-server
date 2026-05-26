package com.lqr.paperragserver.agent.web;

import com.lqr.paperragserver.agent.dto.AgentAskRequest;
import com.lqr.paperragserver.agent.dto.AgentStreamEvent;
import com.lqr.paperragserver.agent.service.AgentService;
import com.lqr.paperragserver.auth.security.SecurityUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.io.IOException;

/**
 * 论文智能体接口控制器，对外提供基于 SSE 的流式问答入口。
 */
@RestController
@RequestMapping("/agent")
public class AgentController {

    private static final long STREAM_TIMEOUT_MILLIS = 240_000L;

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                @Valid @RequestBody AgentAskRequest request) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        Disposable subscription = agentService.streamAnswer(principal.getId(), request)
                .subscribe(
                        event -> sendEvent(emitter, event),
                        emitter::completeWithError,
                        emitter::complete
                );
        emitter.onTimeout(subscription::dispose);
        emitter.onCompletion(subscription::dispose);
        emitter.onError(error -> subscription.dispose());
        return emitter;
    }

    private void sendEvent(SseEmitter emitter, AgentStreamEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event.type())
                    .data(event));
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
    }
}