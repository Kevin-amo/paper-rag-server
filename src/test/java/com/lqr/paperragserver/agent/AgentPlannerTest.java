package com.lqr.paperragserver.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.paperragserver.agent.model.AgentActionType;
import com.lqr.paperragserver.agent.model.AgentDecision;
import com.lqr.paperragserver.agent.service.AgentPlanner;
import com.lqr.paperragserver.agent.tool.AgentToolRegistry;
import com.lqr.paperragserver.ai.service.LlmService;
import com.lqr.paperragserver.ai.service.PromptConstructionService;
import com.lqr.paperragserver.literature.support.LiteratureSearchIntentParser;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentPlannerTest {

    @Test
    void decideShouldPreferExplicitLiteratureLimitAndDateSort() {
        AgentPlanner planner = new AgentPlanner(
                (StubLlmService) prompt -> "{\"thoughtSummary\":\"搜索外部文献。\",\"action\":\"literature_search\",\"actionInput\":{\"query\":\"RAG\",\"limit\":5,\"sortBy\":\"relevance\"},\"finish\":false,\"answer\":null}",
                new ObjectMapper(),
                new AgentToolRegistry(List.of()),
                new LiteratureSearchIntentParser()
        );

        AgentDecision decision = planner.decide("搜索最近的1篇关于RAG的文献", List.of(), List.of(), List.of(), null);

        assertThat(decision.action()).isEqualTo(AgentActionType.LITERATURE_SEARCH);
        assertThat(decision.actionInput()).containsEntry("query", "RAG");
        assertThat(decision.actionInput()).containsEntry("limit", 1);
        assertThat(decision.actionInput()).containsEntry("sortBy", "date");
    }

    private interface StubLlmService extends LlmService {
        @Override
        String generate(PromptConstructionService.Prompt prompt);

        @Override
        default Flux<String> streamGenerate(PromptConstructionService.Prompt prompt) {
            return Flux.empty();
        }

        @Override
        default String generateWithTools(PromptConstructionService.Prompt prompt) {
            return generate(prompt);
        }

        @Override
        default Flux<String> streamGenerateWithTools(PromptConstructionService.Prompt prompt) {
            return Flux.empty();
        }
    }
}