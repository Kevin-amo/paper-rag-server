package com.lqr.paperragserver.agent;

import com.lqr.paperragserver.agent.model.AgentActionType;
import com.lqr.paperragserver.agent.model.AgentDecision;
import com.lqr.paperragserver.agent.model.AgentStreamEvent;
import com.lqr.paperragserver.agent.model.AgentToolResult;
import com.lqr.paperragserver.agent.service.AgentLoop;
import com.lqr.paperragserver.agent.service.AgentPlanner;
import com.lqr.paperragserver.agent.tool.AgentTool;
import com.lqr.paperragserver.agent.tool.AgentToolRegistry;
import com.lqr.paperragserver.common.model.AnswerCitation;
import com.lqr.paperragserver.config.RagProperties;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentLoopTest {

    private final UUID ownerUserId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();

    @Test
    void runShouldExecuteToolAndReturnFinalAnswer() {
        AgentPlanner planner = mock(AgentPlanner.class);
        AgentTool tool = mockTool("local_paper_retrieval", new AgentToolResult(
                "找到 1 个相关片段。",
                "本地论文证据",
                List.of(new AnswerCitation("source-1", "chunk-1", 1, "Paper A", "excerpt", 0.9)),
                Map.of("localPaperChunks", List.of(Map.of("chunkId", "chunk-1")))
        ));
        AgentLoop loop = new AgentLoop(planner, new AgentToolRegistry(List.of(tool)), ragProperties());
        when(planner.decide(eq("问题"), anyList(), anyList(), anyList(), eq(3)))
                .thenReturn(new AgentDecision(
                        "先检索本地论文。",
                        AgentActionType.LOCAL_PAPER_RETRIEVAL,
                        Map.of("query", "问题", "topK", 3),
                        false,
                        null
                ))
                .thenReturn(AgentDecision.finish("证据足够。", "最终回答"));
        when(planner.finalAnswer(eq("问题"), anyList(), anyList(), anyList())).thenReturn("最终回答");
        List<AgentStreamEvent> events = new ArrayList<>();

        AgentLoop.AgentLoopResult result = loop.run(ownerUserId, conversationId, "问题", 3, List.of(), events::add);

        assertThat(result.draftAnswer()).isEqualTo("最终回答");
        assertThat(result.observations()).contains("本地论文证据");
        assertThat(result.citations()).hasSize(1);
        assertThat(result.metadata()).containsEntry("type", "AGENT_RESULT");
        assertThat(result.metadata()).containsKey("localPaperChunks");
        assertThat(events.stream().map(AgentStreamEvent::type)).contains("step", "thought", "tool_call", "tool_result");
        verify(tool).execute(eq(ownerUserId), any());
        verify(planner, never()).finalAnswer(eq("问题"), anyList(), anyList(), anyList());
    }

    @Test
    void runShouldLimitSingleLocalRetrievalCitationsByTopK() {
        AgentPlanner planner = mock(AgentPlanner.class);
        AgentTool tool = mockTool("local_paper_retrieval", new AgentToolResult(
                "找到 10 个相关片段。",
                "本地论文证据",
                citations("single", 10, 0.1),
                Map.of("localPaperChunks", List.of())
        ));
        AgentLoop loop = new AgentLoop(planner, new AgentToolRegistry(List.of(tool)), ragProperties());
        when(planner.decide(eq("问题"), anyList(), anyList(), anyList(), eq(8)))
                .thenReturn(new AgentDecision(
                        "先检索本地论文。",
                        AgentActionType.LOCAL_PAPER_RETRIEVAL,
                        Map.of("query", "问题", "topK", 8),
                        false,
                        null
                ))
                .thenReturn(AgentDecision.finish("证据足够。", "最终回答"));

        AgentLoop.AgentLoopResult result = loop.run(ownerUserId, conversationId, "问题", 8, List.of(), event -> {
        });

        assertThat(result.citations()).hasSize(8);
        assertThat(result.citations()).extracting(AnswerCitation::chunkId)
                .doesNotContain("chunk-single-0", "chunk-single-1");
        assertThat(result.citations()).isSortedAccordingTo((left, right) -> Double.compare(right.rankScore(), left.rankScore()));
    }

    @Test
    void runShouldStopConsecutiveSameToolWithDifferentInputs() {
        AgentPlanner planner = mock(AgentPlanner.class);
        AgentTool tool = mockTool("local_paper_retrieval",
                new AgentToolResult("第一次找到 8 个相关片段。", "第一次本地论文证据", citations("first", 8, 0.1), Map.of()),
                new AgentToolResult("第二次找到 8 个相关片段。", "第二次本地论文证据", citations("second", 8, 10.1), Map.of())
        );
        AgentLoop loop = new AgentLoop(planner, new AgentToolRegistry(List.of(tool)), ragProperties());
        when(planner.decide(eq("问题"), anyList(), anyList(), anyList(), eq(8)))
                .thenReturn(new AgentDecision(
                        "先检索一个角度。",
                        AgentActionType.LOCAL_PAPER_RETRIEVAL,
                        Map.of("query", "问题 A", "topK", 8),
                        false,
                        null
                ))
                .thenReturn(new AgentDecision(
                        "继续检索另一个角度。",
                        AgentActionType.LOCAL_PAPER_RETRIEVAL,
                        Map.of("query", "问题 B", "topK", 8),
                        false,
                        null
                ));

        AgentLoop.AgentLoopResult result = loop.run(ownerUserId, conversationId, "问题", 8, List.of(), event -> {
        });

        assertThat(result.metadata()).containsEntry("stopReason", "REPEATED_ACTION");
        assertThat(result.steps()).hasSize(1);
        assertThat(result.observations()).containsExactly("第一次本地论文证据");
        assertThat(result.citations()).hasSize(8);
        assertThat(result.citations()).allMatch(citation -> citation.chunkId().startsWith("chunk-first-"));
        verify(tool, times(1)).execute(eq(ownerUserId), any());
    }

    @Test
    void runShouldDeduplicateCitationsAndKeepHigherRankScore() {
        AgentPlanner planner = mock(AgentPlanner.class);
        AnswerCitation lower = new AnswerCitation("source-1", "chunk-dup", 1, "Paper A", "lower", 0.2);
        AnswerCitation higher = new AnswerCitation("source-1", "chunk-dup", 1, "Paper A", "higher", 0.95);
        AgentTool tool = mockTool("local_paper_retrieval",
                new AgentToolResult("找到 2 个相关片段。", "本地论文证据", List.of(lower, higher), Map.of())
        );
        AgentLoop loop = new AgentLoop(planner, new AgentToolRegistry(List.of(tool)), ragProperties());
        when(planner.decide(eq("问题"), anyList(), anyList(), anyList(), eq(8)))
                .thenReturn(new AgentDecision(
                        "先检索本地论文。",
                        AgentActionType.LOCAL_PAPER_RETRIEVAL,
                        Map.of("query", "问题", "topK", 8),
                        false,
                        null
                ))
                .thenReturn(AgentDecision.finish("证据足够。", "最终回答"));

        AgentLoop.AgentLoopResult result = loop.run(ownerUserId, conversationId, "问题", 8, List.of(), event -> {
        });

        assertThat(result.citations()).containsExactly(higher);
    }

    @Test
    void runShouldKeepLiteratureResultsInMetadataWithoutAddingCitations() {
        AgentPlanner planner = mock(AgentPlanner.class);
        List<Map<String, Object>> literatureItems = literatureItems(12);
        AgentTool tool = mockTool("literature_search", new AgentToolResult(
                "外部文献搜索完成，找到 12 篇论文。",
                "外部文献证据",
                List.of(),
                Map.of("literature", Map.of(
                        "type", "LITERATURE_SEARCH_RESULT",
                        "query", "RAG",
                        "items", literatureItems
                ))
        ));
        AgentLoop loop = new AgentLoop(planner, new AgentToolRegistry(List.of(tool)), ragProperties());
        when(planner.decide(eq("问题"), anyList(), anyList(), anyList(), eq(3)))
                .thenReturn(new AgentDecision(
                        "搜索外部文献。",
                        AgentActionType.LITERATURE_SEARCH,
                        Map.of("query", "RAG", "limit", 12),
                        false,
                        null
                ))
                .thenReturn(AgentDecision.finish("证据足够。", "最终回答"));

        AgentLoop.AgentLoopResult result = loop.run(ownerUserId, conversationId, "问题", 3, List.of(), event -> {
        });

        assertThat(result.citations()).isEmpty();
        assertThat(result.metadata()).containsKey("literature");
        @SuppressWarnings("unchecked")
        Map<String, Object> literature = (Map<String, Object>) result.metadata().get("literature");
        assertThat((List<?>) literature.get("items")).hasSize(12);
    }

    @Test
    void runShouldUseDefaultTopKWhenTopKIsNull() {
        AgentPlanner planner = mock(AgentPlanner.class);
        AgentTool tool = mockTool("local_paper_retrieval", new AgentToolResult(
                "找到 5 个相关片段。",
                "本地论文证据",
                citations("default", 5, 0.1),
                Map.of()
        ));
        AgentLoop loop = new AgentLoop(planner, new AgentToolRegistry(List.of(tool)), ragProperties());
        when(planner.decide(eq("问题"), anyList(), anyList(), anyList(), eq(null)))
                .thenReturn(new AgentDecision(
                        "先检索本地论文。",
                        AgentActionType.LOCAL_PAPER_RETRIEVAL,
                        Map.of("query", "问题"),
                        false,
                        null
                ))
                .thenReturn(AgentDecision.finish("证据足够。", "最终回答"));

        AgentLoop.AgentLoopResult result = loop.run(ownerUserId, conversationId, "问题", null, List.of(), event -> {
        });

        assertThat(result.citations()).hasSize(3);
    }

    @Test
    void runShouldOverrideUnsupportedLiteratureThoughtSummary() {
        AgentPlanner planner = mock(AgentPlanner.class);
        AgentTool tool = mockTool("literature_search", new AgentToolResult(
                "外部文献搜索完成，找到 1 篇论文。",
                "[外部文献] Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks",
                List.of(),
                Map.of("literature", Map.of(
                        "type", "LITERATURE_SEARCH_RESULT",
                        "query", "RAG",
                        "items", List.of(Map.of("title", "RAG Paper"))
                ))
        ));
        AgentLoop loop = new AgentLoop(planner, new AgentToolRegistry(List.of(tool)), ragProperties());
        when(planner.decide(eq("给我搜一篇关于 RAG 的文献，要最新的"), anyList(), anyList(), anyList(), eq(3)))
                .thenReturn(new AgentDecision(
                        "虽然本地知识库有3篇相关论文，但用户需要最新文献",
                        AgentActionType.LITERATURE_SEARCH,
                        Map.of("query", "RAG", "limit", 1, "sortBy", "date"),
                        false,
                        null
                ))
                .thenReturn(AgentDecision.finish("证据足够。", "最终回答"));
        List<AgentStreamEvent> events = new ArrayList<>();

        AgentLoop.AgentLoopResult result = loop.run(ownerUserId, conversationId, "给我搜一篇关于 RAG 的文献，要最新的", 3, List.of(), events::add);

        assertThat(events.stream()
                .filter(event -> "thought".equals(event.type()))
                .map(AgentStreamEvent::thought))
                .noneMatch(thought -> thought != null && thought.contains("本地知识库有3篇"));
        assertThat(result.metadata().get("steps").toString()).doesNotContain("本地知识库有3篇");
        assertThat(result.steps()).hasSize(1);
        assertThat(result.steps().get(0).action()).isEqualTo("literature_search");
        verify(tool).execute(eq(ownerUserId), any());
    }

    @Test
    void runShouldStopRepeatedActionAndReturnFallbackAnswer() {
        AgentPlanner planner = mock(AgentPlanner.class);
        AgentTool tool = mockTool("literature_search", new AgentToolResult(
                "找到 0 篇论文。",
                "未找到外部文献结果。",
                List.of(),
                Map.of("literature", Map.of("type", "LITERATURE_SEARCH_RESULT", "query", "问题", "items", List.of()))
        ));
        AgentLoop loop = new AgentLoop(planner, new AgentToolRegistry(List.of(tool)), ragProperties());
        when(planner.decide(eq("问题"), anyList(), anyList(), anyList(), eq(null)))
                .thenReturn(new AgentDecision(
                        "继续搜索。",
                        AgentActionType.LITERATURE_SEARCH,
                        Map.of("query", "问题"),
                        false,
                        null
                ));
        when(planner.finalAnswer(eq("问题"), anyList(), anyList(), anyList())).thenReturn("达到步数上限后的回答");

        AgentLoop.AgentLoopResult result = loop.run(ownerUserId, conversationId, "问题", null, List.of(), event -> {
        });

        assertThat(result.draftAnswer()).isNull();
        assertThat(result.observations()).contains("未找到外部文献结果。");
        assertThat(result.metadata()).containsEntry("stopReason", "REPEATED_ACTION");
        verify(planner, never()).finalAnswer(eq("问题"), anyList(), anyList(), anyList());
        verify(tool, times(1)).execute(eq(ownerUserId), any());
    }

    @Test
    void runShouldReturnAnswerWhenToolThrowsUnavailableError() {
        AgentPlanner planner = mock(AgentPlanner.class);
        AgentTool tool = mock(AgentTool.class);
        when(tool.name()).thenReturn("literature_search");
        when(tool.description()).thenReturn("literature_search description");
        when(tool.execute(any(), any())).thenThrow(new RuntimeException("外部文献服务暂不可用，请稍后重试"));
        AgentLoop loop = new AgentLoop(planner, new AgentToolRegistry(List.of(tool)), ragProperties());
        when(planner.decide(eq("问题"), anyList(), anyList(), anyList(), eq(null)))
                .thenReturn(new AgentDecision(
                        "先搜索外部文献。",
                        AgentActionType.LITERATURE_SEARCH,
                        Map.of("query", "问题"),
                        false,
                        null
                ));
        when(planner.finalAnswer(eq("问题"), anyList(), anyList(), anyList())).thenReturn("外部文献服务暂不可用，请稍后重试。可以先换一个检索目标或稍后再试。");
        List<AgentStreamEvent> events = new ArrayList<>();

        AgentLoop.AgentLoopResult result = loop.run(ownerUserId, conversationId, "问题", null, List.of(), events::add);

        assertThat(result.draftAnswer()).isNull();
        assertThat(result.observations()).anyMatch(observation -> observation.contains("外部文献服务暂不可用"));
        assertThat(result.metadata()).containsEntry("stopReason", "TOOL_UNAVAILABLE");
        assertThat(events.stream().map(AgentStreamEvent::type)).contains("tool_result");
        verify(tool, times(1)).execute(eq(ownerUserId), any());
    }

    private AgentTool mockTool(String name, AgentToolResult result, AgentToolResult... nextResults) {
        AgentTool tool = mock(AgentTool.class);
        when(tool.name()).thenReturn(name);
        when(tool.description()).thenReturn(name + " description");
        when(tool.execute(any(), any())).thenReturn(result, nextResults);
        return tool;
    }

    private List<AnswerCitation> citations(String prefix, int count, double baseScore) {
        List<AnswerCitation> citations = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            citations.add(new AnswerCitation(
                    "source-" + prefix,
                    "chunk-" + prefix + '-' + index,
                    index,
                    "Paper " + prefix,
                    "excerpt " + index,
                    baseScore + index
            ));
        }
        return citations;
    }

    private List<Map<String, Object>> literatureItems(int count) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            items.add(Map.of("title", "Paper " + index));
        }
        return items;
    }

    private RagProperties ragProperties() {
        return new RagProperties(800, 120, 3, 0);
    }
}
