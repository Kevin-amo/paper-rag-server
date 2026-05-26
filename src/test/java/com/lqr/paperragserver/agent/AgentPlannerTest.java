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

/**
 * AgentPlanner 的规划测试，覆盖动作选择、参数归一化、降级决策和流式最终回答。
 */
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

    @Test
    void decideShouldNotApplyTopKToLiteratureSearch() {
        AgentPlanner planner = new AgentPlanner(
                (StubLlmService) prompt -> "{\"thoughtSummary\":\"搜索外部文献。\",\"action\":\"literature_search\",\"actionInput\":{\"query\":\"RAG\",\"topK\":3,\"limit\":5},\"finish\":false,\"answer\":null}",
                new ObjectMapper(),
                new AgentToolRegistry(List.of()),
                new LiteratureSearchIntentParser()
        );

        AgentDecision decision = planner.decide("搜索关于RAG的最新文献", List.of(), List.of(), List.of(), 3);

        assertThat(decision.action()).isEqualTo(AgentActionType.LITERATURE_SEARCH);
        assertThat(decision.actionInput()).doesNotContainKey("topK");
        assertThat(decision.actionInput()).containsEntry("limit", 5);
    }

    @Test
    void decideShouldApplyTopKOnlyToLocalPaperRetrieval() {
        AgentPlanner planner = new AgentPlanner(
                (StubLlmService) prompt -> "{\"thoughtSummary\":\"检索本地论文。\",\"action\":\"local_paper_retrieval\",\"actionInput\":{\"query\":\"RAG\"},\"finish\":false,\"answer\":null}",
                new ObjectMapper(),
                new AgentToolRegistry(List.of()),
                new LiteratureSearchIntentParser()
        );

        AgentDecision decision = planner.decide("总结我上传的RAG论文", List.of(), List.of(), List.of(), 3);

        assertThat(decision.action()).isEqualTo(AgentActionType.LOCAL_PAPER_RETRIEVAL);
        assertThat(decision.actionInput()).containsEntry("topK", 3);
    }

    @Test
    void decideShouldPreferRequestTopKOverModelLocalTopK() {
        AgentPlanner planner = new AgentPlanner(
                (StubLlmService) prompt -> "{\"thoughtSummary\":\"检索本地论文。\",\"action\":\"local_paper_retrieval\",\"actionInput\":{\"query\":\"RAG\",\"topK\":5},\"finish\":false,\"answer\":null}",
                new ObjectMapper(),
                new AgentToolRegistry(List.of()),
                new LiteratureSearchIntentParser()
        );

        AgentDecision decision = planner.decide("总结我上传的RAG论文", List.of(), List.of(), List.of(), 10);

        assertThat(decision.action()).isEqualTo(AgentActionType.LOCAL_PAPER_RETRIEVAL);
        assertThat(decision.actionInput()).containsEntry("topK", 10);
    }

    @Test
    void fallbackLiteratureSearchShouldNotCarryTopK() {
        AgentPlanner planner = new AgentPlanner(
                (StubLlmService) prompt -> {
                    throw new RuntimeException("quota exhausted");
                },
                new ObjectMapper(),
                new AgentToolRegistry(List.of()),
                new LiteratureSearchIntentParser()
        );

        AgentDecision decision = planner.decide("搜索关于RAG的最新文献", List.of(), List.of(), List.of(), 3);

        assertThat(decision.action()).isEqualTo(AgentActionType.LITERATURE_SEARCH);
        assertThat(decision.actionInput()).doesNotContainKey("topK");
        assertThat(decision.actionInput()).containsEntry("limit", 5);
    }

    @Test
    void fallbackLocalRetrievalShouldCarryTopK() {
        AgentPlanner planner = new AgentPlanner(
                (StubLlmService) prompt -> {
                    throw new RuntimeException("quota exhausted");
                },
                new ObjectMapper(),
                new AgentToolRegistry(List.of()),
                new LiteratureSearchIntentParser()
        );

        AgentDecision decision = planner.decide("总结我上传的RAG论文", List.of(), List.of(), List.of(), 3);

        assertThat(decision.action()).isEqualTo(AgentActionType.LOCAL_PAPER_RETRIEVAL);
        assertThat(decision.actionInput()).containsEntry("topK", 3);
    }

    @Test
    void decideShouldFinishWithExistingObservationsWhenModelFails() {
        AgentPlanner planner = new AgentPlanner(
                (StubLlmService) prompt -> {
                    throw new RuntimeException("quota exhausted");
                },
                new ObjectMapper(),
                new AgentToolRegistry(List.of()),
                new LiteratureSearchIntentParser()
        );

        AgentDecision decision = planner.decide("搜索 MCP 文献", List.of(), List.of(), List.of("[外部文献] MCP paper"), null);

        assertThat(decision.finish()).isTrue();
        assertThat(decision.action()).isEqualTo(AgentActionType.FINISH);
        assertThat(decision.answer()).contains("MCP paper");
    }

    @Test
    void finalAnswerShouldFallbackToObservationsWhenModelFails() {
        AgentPlanner planner = new AgentPlanner(
                (StubLlmService) prompt -> {
                    throw new RuntimeException("quota exhausted");
                },
                new ObjectMapper(),
                new AgentToolRegistry(List.of()),
                new LiteratureSearchIntentParser()
        );

        String answer = planner.finalAnswer("搜索 MCP 文献", List.of(), List.of(), List.of("[外部文献] MCP paper"));

        assertThat(answer).contains("MCP paper");
    }

    @Test
    void finalAnswerStreamShouldReturnModelDeltas() {
        AgentPlanner planner = new AgentPlanner(
                new StubLlmService() {
                    @Override
                    public String generate(PromptConstructionService.Prompt prompt) {
                        return "sync answer";
                    }

                    @Override
                    public Flux<String> streamGenerate(PromptConstructionService.Prompt prompt) {
                        return Flux.just("hello ", "world");
                    }
                },
                new ObjectMapper(),
                new AgentToolRegistry(List.of()),
                new LiteratureSearchIntentParser()
        );

        List<String> deltas = planner.finalAnswerStream("问题", List.of(), List.of(), List.of("证据"))
                .collectList()
                .block();

        assertThat(deltas).containsExactly("hello ", "world");
    }

    /**
     * 测试用 LLM 服务契约，用于以固定输出替代真实模型调用。
     */
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