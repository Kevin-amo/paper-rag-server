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
        AgentLoop loop = new AgentLoop(planner, new AgentToolRegistry(List.of(tool)));
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
        AgentLoop loop = new AgentLoop(planner, new AgentToolRegistry(List.of(tool)));
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
        AgentLoop loop = new AgentLoop(planner, new AgentToolRegistry(List.of(tool)));
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
        AgentLoop loop = new AgentLoop(planner, new AgentToolRegistry(List.of(tool)));
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

    private AgentTool mockTool(String name, AgentToolResult result) {
        AgentTool tool = mock(AgentTool.class);
        when(tool.name()).thenReturn(name);
        when(tool.description()).thenReturn(name + " description");
        when(tool.execute(any(), any())).thenReturn(result);
        return tool;
    }
}