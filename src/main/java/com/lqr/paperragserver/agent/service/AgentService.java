package com.lqr.paperragserver.agent.service;

import com.lqr.paperragserver.agent.dto.AgentAskRequest;
import com.lqr.paperragserver.agent.dto.AgentStreamEvent;
import com.lqr.paperragserver.common.logging.LogSanitizer;
import com.lqr.paperragserver.conversation.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 论文智能体对话服务，负责创建或接续会话、驱动执行循环，并以流式事件输出回答过程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final ConversationService conversationService;
    private final AgentLoop agentLoop;
    private final AgentPlanner planner;

    /**
     * 处理一次智能体流式问答，负责会话解析、执行循环、最终回答生成和消息持久化。
     *
     * @param ownerUserId 当前用户标识
     * @param request     智能体问答请求
     * @return 按执行阶段输出的流式事件序列
     */
    public Flux<AgentStreamEvent> streamAnswer(UUID ownerUserId, AgentAskRequest request) {
        return Flux.<AgentStreamEvent>create(sink -> {
            UUID activeConversationId = request.conversationId();
            long startNanos = System.nanoTime();
            log.info("agent.ask.start ownerUserId={} conversationId={} questionLength={} questionExcerpt={} topK={}",
                    ownerUserId, activeConversationId, textLength(request.question()), LogSanitizer.safeExcerpt(request.question(), 160), request.topK());
            try {
                ConversationService.ConversationView conversation = resolveConversation(ownerUserId, activeConversationId, request.question());
                UUID resolvedConversationId = conversation.id();
                activeConversationId = resolvedConversationId;
                sink.next(AgentStreamEvent.start(resolvedConversationId));
                conversationService.appendUserMessage(ownerUserId, resolvedConversationId, request.question());
                List<ConversationService.MessageView> history = conversationService.recentMessages(
                        ownerUserId,
                        resolvedConversationId,
                        ConversationService.DEFAULT_HISTORY_MESSAGE_LIMIT
                );
                log.info("agent.ask.context ownerUserId={} conversationId={} historyCount={} topK={}",
                        ownerUserId, resolvedConversationId, history.size(), request.topK());

                AgentLoop.AgentLoopResult result = agentLoop.run(
                        ownerUserId,
                        resolvedConversationId,
                        request.question(),
                        request.topK(),
                        history,
                        sink::next
                );
                String answer = streamFinalAnswer(resolvedConversationId, request.question(), history, result, sink::next);
                conversationService.appendAssistantMessage(
                        ownerUserId,
                        resolvedConversationId,
                        answer,
                        result.citations(),
                        result.metadata()
                );
                log.info("agent.ask.done ownerUserId={} conversationId={} answerLength={} citationCount={} stepCount={} costMs={}",
                        ownerUserId, resolvedConversationId, textLength(answer), result.citations().size(), result.steps().size(), elapsedMs(startNanos));
                sink.next(AgentStreamEvent.done(resolvedConversationId, answer, result.citations(), result.metadata()));
                sink.complete();
            } catch (RuntimeException ex) {
                log.error("agent.ask.failed ownerUserId={} conversationId={} questionLength={} costMs={}",
                        ownerUserId, activeConversationId, textLength(request.question()), elapsedMs(startNanos), ex);
                sink.next(AgentStreamEvent.error(activeConversationId, userSafeMessage(ex)));
                sink.complete();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 根据请求中的会话标识决定创建新会话或加载已有会话。
     *
     * @param ownerUserId    当前用户标识
     * @param conversationId 请求携带的会话标识；为空时创建新会话
     * @param question       用户本轮问题，用于新会话标题或初始上下文
     * @return 可继续写入消息的会话视图
     */
    private ConversationService.ConversationView resolveConversation(UUID ownerUserId, UUID conversationId, String question) {
        if (conversationId == null) {
            return conversationService.createConversation(ownerUserId, question);
        }
        return conversationService.requireConversation(ownerUserId, conversationId);
    }

    /**
     * 使用最终回答生成器输出回答增量，并在空流时回退到非流式回答。
     *
     * @param conversationId 当前会话标识
     * @param question       用户本轮问题
     * @param history        最近会话历史
     * @param result         智能体执行循环结果
     * @param sink           流式事件消费者
     * @return 完整最终回答文本
     */
    private String streamFinalAnswer(UUID conversationId,
                                     String question,
                                     List<ConversationService.MessageView> history,
                                     AgentLoop.AgentLoopResult result,
                                     Consumer<AgentStreamEvent> sink) {
        long startNanos = System.nanoTime();
        log.info("agent.answer.start conversationId={} stepCount={} observationCount={} citationCount={}",
                conversationId, result.steps().size(), result.observations().size(), result.citations().size());
        StringBuilder answerBuffer = new StringBuilder();
        RuntimeException streamFailure = null;
        try {
            planner.finalAnswerStream(question, history, result.steps(), result.observations())
                    .doOnNext(delta -> emitAnswerDelta(conversationId, delta, answerBuffer, sink))
                    .blockLast();
        } catch (RuntimeException ex) {
            streamFailure = ex;
            log.warn("agent.answer.failed conversationId={} partialAnswerLength={} costMs={}",
                    conversationId, answerBuffer.length(), elapsedMs(startNanos), ex);
        }

        if (streamFailure != null && answerBuffer.length() > 0) {
            throw streamFailure;
        }
        if (answerBuffer.length() == 0) {
            log.warn("agent.answer.fallback conversationId={} reason=EMPTY_STREAM", conversationId);
            emitAnswerDelta(conversationId, fallbackFinalAnswer(question, history, result), answerBuffer, sink);
        }
        log.info("agent.answer.done conversationId={} answerLength={} costMs={}", conversationId, answerBuffer.length(), elapsedMs(startNanos));
        log.debug("agent.answer.debug conversationId={} answerExcerpt={}", conversationId, LogSanitizer.safeExcerpt(answerBuffer.toString(), 500));
        return answerBuffer.toString();
    }

    /**
     * 追加并发送回答增量，忽略空片段以保持事件流紧凑。
     *
     * @param conversationId 当前会话标识
     * @param delta          模型返回的增量文本
     * @param answerBuffer   已累计的回答内容
     * @param sink           流式事件消费者
     */
    private void emitAnswerDelta(UUID conversationId,
                                 String delta,
                                 StringBuilder answerBuffer,
                                 Consumer<AgentStreamEvent> sink) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        answerBuffer.append(delta);
        sink.accept(AgentStreamEvent.delta(conversationId, delta));
    }

    /**
     * 生成最终回答的兜底内容，优先使用规划器回答，其次使用执行循环草稿。
     *
     * @param question 用户本轮问题
     * @param history  最近会话历史
     * @param result   智能体执行循环结果
     * @return 可展示的最终回答文本
     */
    private String fallbackFinalAnswer(String question,
                                       List<ConversationService.MessageView> history,
                                       AgentLoop.AgentLoopResult result) {
        String answer = planner.finalAnswer(question, history, result.steps(), result.observations());
        if ((answer == null || answer.isBlank()) && result.draftAnswer() != null && !result.draftAnswer().isBlank()) {
            answer = result.draftAnswer();
        }
        if (answer == null || answer.isBlank()) {
            return "智能体未返回回答内容";
        }
        return answer;
    }

    /**
     * 将纳秒起点换算为毫秒耗时，用于日志记录。
     *
     * @param startNanos 起始纳秒时间
     * @return 已经过的毫秒数
     */
    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    /**
     * 计算文本长度，空文本按 0 处理。
     *
     * @param text 待统计文本
     * @return 文本长度
     */
    private int textLength(String text) {
        return text == null ? 0 : text.length();
    }

    /**
     * 提取可返回给用户的安全错误信息。
     *
     * @param ex 执行异常
     * @return 用户可见的错误提示
     */
    private String userSafeMessage(RuntimeException ex) {
        if (ex == null || ex.getMessage() == null || ex.getMessage().isBlank()) {
            return "智能体执行失败";
        }
        return ex.getMessage().trim();
    }
}