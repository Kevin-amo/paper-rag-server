package com.lqr.papermind.document.structured;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.papermind.ai.service.LlmService;
import com.lqr.papermind.ai.service.PromptConstructionService;
import com.lqr.papermind.document.service.DocumentPersistenceService;
import com.lqr.papermind.document.structured.model.ModelCompletionResult;
import com.lqr.papermind.document.structured.model.PaperStructuredContent;
import com.lqr.papermind.document.structured.model.PaperStructuredContentSupport;
import com.lqr.papermind.document.structured.model.StructuredFieldEvidence;
import com.lqr.papermind.document.structured.model.StructuredParseResult;
import com.lqr.papermind.document.structured.service.impl.PaperStructuredModelCompleterImpl;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaperStructuredModelCompleterTest {

    @Test
    void completeShouldRetryWithJsonRepairPromptWhenFirstOutputHasNoJsonObject() {
        LlmService llmService = mock(LlmService.class);
        PaperStructuredModelCompleterImpl completer = new PaperStructuredModelCompleterImpl(llmService, new ObjectMapper());
        StructuredParseResult ruleResult = new StructuredParseResult(
                PaperStructuredContentSupport.emptyContent(),
                PaperStructuredContentSupport.emptyEvidence("RULE"),
                List.of("researchObject"),
                List.of()
        );
        when(llmService.generate(any(PromptConstructionService.Prompt.class)))
                .thenReturn("无法根据文本判断")
                .thenReturn("{\"researchObject\":\"博客系统\",\"keywords\":[],\"innovationPoints\":[],\"mainConclusions\":[]}");

        ModelCompletionResult result = completer.complete(document(), ruleResult);

        assertThat(result.errorMessage()).isNull();
        assertThat(result.result().content().researchObject()).isEqualTo("博客系统");
        assertThat(result.rawModelOutput()).contains("无法根据文本判断").contains("博客系统");
        verify(llmService, times(2)).generate(any(PromptConstructionService.Prompt.class));
    }

    @Test
    void completeShouldUseFirstBalancedJsonObjectWhenOutputHasTrailingExplanation() {
        LlmService llmService = mock(LlmService.class);
        PaperStructuredModelCompleterImpl completer = new PaperStructuredModelCompleterImpl(llmService, new ObjectMapper());
        StructuredParseResult ruleResult = new StructuredParseResult(
                PaperStructuredContentSupport.emptyContent(),
                PaperStructuredContentSupport.emptyEvidence("RULE"),
                List.of("researchObject"),
                List.of()
        );
        when(llmService.generate(any(PromptConstructionService.Prompt.class)))
                .thenReturn("""
                        {"researchObject":"blog system","keywords":[],"innovationPoints":[],"mainConclusions":[]}

                        Explanation: fields not supported by evidence remain empty. {not-json}
                        """);

        ModelCompletionResult result = completer.complete(document(), ruleResult);

        assertThat(result.errorMessage()).isNull();
        assertThat(result.result().content().researchObject()).isEqualTo("blog system");
        verify(llmService, times(1)).generate(any(PromptConstructionService.Prompt.class));
    }

    @Test
    void completeShouldDeriveFieldsFromRuleResultWhenModelOutputsNoJsonTwice() {
        LlmService llmService = mock(LlmService.class);
        PaperStructuredModelCompleterImpl completer = new PaperStructuredModelCompleterImpl(llmService, new ObjectMapper());
        PaperStructuredContent ruleContent = new PaperStructuredContent(
                "软件分析与建模技术课程设计",
                null,
                "本课程设计围绕博客系统展开。",
                null,
                "系统需要支持用户登录、文章管理和评论管理。",
                "对登录、发布文章和评论流程进行测试。",
                null,
                "本文完成博客系统主要功能设计与实现。",
                null,
                List.of("博客系统"),
                null,
                null,
                List.of(),
                null,
                null,
                List.of()
        );
        StructuredParseResult ruleResult = new StructuredParseResult(
                ruleContent,
                Map.of(
                        "title", new StructuredFieldEvidence("title", "RULE", 0.85, false, null),
                        "introduction", new StructuredFieldEvidence("introduction", "RULE", 0.86, false, null),
                        "methodology", new StructuredFieldEvidence("methodology", "RULE", 0.86, false, null),
                        "experimentResults", new StructuredFieldEvidence("experimentResults", "RULE", 0.86, false, null),
                        "conclusion", new StructuredFieldEvidence("conclusion", "RULE", 0.86, false, null)
                ),
                List.of("researchObject", "researchQuestion", "methodPath", "experimentDataSummary", "mainConclusions"),
                List.of()
        );
        when(llmService.generate(any(PromptConstructionService.Prompt.class)))
                .thenReturn("无法根据文本判断")
                .thenReturn("仍然无法输出");

        ModelCompletionResult result = completer.complete(document(), ruleResult);

        assertThat(result.errorMessage()).contains("缺少 JSON 对象");
        assertThat(result.result().content().researchObject()).isEqualTo("软件分析与建模技术");
        assertThat(result.result().content().researchQuestion()).contains("博客系统");
        assertThat(result.result().content().methodPath()).contains("用户登录");
        assertThat(result.result().content().experimentDataSummary()).contains("发布文章");
        assertThat(result.result().content().mainConclusions()).contains("本文完成博客系统主要功能设计与实现。");
    }

    @Test
    void completeShouldKeepFirstAndRepairOutputsWhenBothHaveNoJsonObject() {
        LlmService llmService = mock(LlmService.class);
        PaperStructuredModelCompleterImpl completer = new PaperStructuredModelCompleterImpl(llmService, new ObjectMapper());
        StructuredParseResult ruleResult = new StructuredParseResult(
                PaperStructuredContentSupport.emptyContent(),
                PaperStructuredContentSupport.emptyEvidence("RULE"),
                List.of("researchObject"),
                List.of()
        );
        when(llmService.generate(any(PromptConstructionService.Prompt.class)))
                .thenReturn("无法根据文本判断")
                .thenReturn("仍然无法输出");

        ModelCompletionResult result = completer.complete(document(), ruleResult);

        assertThat(result.errorMessage()).contains("缺少 JSON 对象");
        assertThat(result.rawModelOutput())
                .contains("第一次输出：")
                .contains("无法根据文本判断")
                .contains("修复输出：")
                .contains("仍然无法输出");
    }

    private DocumentPersistenceService.DocumentDetail document() {
        OffsetDateTime now = OffsetDateTime.now();
        return new DocumentPersistenceService.DocumentDetail(
                "source-1",
                UUID.randomUUID(),
                "软件分析与建模技术课程设计",
                "paper.docx",
                "paper.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                100L,
                List.of(),
                null,
                null,
                null,
                null,
                List.of(),
                "本文围绕博客系统开展软件分析与建模。",
                Map.of(),
                "INDEXED",
                1,
                null,
                now,
                now,
                null
        );
    }
}
