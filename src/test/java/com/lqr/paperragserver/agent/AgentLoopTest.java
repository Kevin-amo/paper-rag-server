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

        assertThat(result.answer()).isEqualTo("最终回答");
        assertThat(result.citations()).hasSize(1);
        assertThat(result.metadata()).containsEntry("type", "AGENT_RESULT");
        assertThat(result.metadata()).containsKey("localPaperChunks");
        assertThat(events.stream().map(AgentStreamEvent::type)).contains("step", "thought", "tool_call", "tool_result");
        verify(tool).execute(eq(ownerUserId), any());
    }

    @Test
    void runShouldStopAtMaxStepsAndReturnFallbackAnswer() {
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

        assertThat(result.answer()).isEqualTo("达到步数上限后的回答");
        assertThat(result.metadata()).containsEntry("stopReason", "MAX_STEPS_REACHED");
        verify(tool, times(5)).execute(eq(ownerUserId), any());
    }

    private AgentTool mockTool(String name, AgentToolResult result) {
        AgentTool tool = mock(AgentTool.class);
        when(tool.name()).thenReturn(name);
        when(tool.description()).thenReturn(name + " description");
        when(tool.execute(any(), any())).thenReturn(result);
        return tool;
    }
}