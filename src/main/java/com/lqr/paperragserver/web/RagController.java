package com.lqr.paperragserver.web;

import com.lqr.paperragserver.auth.security.SecurityUserPrincipal;
import com.lqr.paperragserver.common.model.RagAnswer;
import com.lqr.paperragserver.rag.service.RagAnswerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * RAG 问答接口。
 *
 * <p>该接口接收用户问题，返回大模型回答以及参与回答生成的引用片段。</p>
 */
@RestController
@RequestMapping("/rag")
public class RagController {

    private final RagAnswerService ragAnswerService;

    /**
     * 创建 RAG 问答控制器。
     *
     * @param ragAnswerService 问答服务
     */
    public RagController(RagAnswerService ragAnswerService) {
        this.ragAnswerService = ragAnswerService;
    }

    /**
     * 根据用户问题生成答案。
     *
     * @param request 问答请求
     * @return 包含答案和引用的结果
     */
    @PostMapping("/ask")
    public RagAnswer ask(@AuthenticationPrincipal SecurityUserPrincipal principal,
                         @Valid @RequestBody AskRequest request) {
        return ragAnswerService.answer(principal.getId(), request.conversationId(), request.question(), request.topK());
    }

    /**
     * 问答请求体。
     *
     * @param question 用户问题
     * @param topK 可选召回数量
     */
    public record AskRequest(
            UUID conversationId,
            @NotBlank(message = "问题不能为空") String question,
            @Positive(message = "topK 必须为正数") Integer topK
    ) {
    }
}