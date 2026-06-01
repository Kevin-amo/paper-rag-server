package com.lqr.paperragserver.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.paperragserver.agent.core.AgentActionType;
import com.lqr.paperragserver.agent.core.AgentDecision;
import com.lqr.paperragserver.agent.paper.LiteratureContextPolicy;
import com.lqr.paperragserver.agent.paper.LiteratureFollowUpPolicy;
import com.lqr.paperragserver.agent.planning.AgentDecisionParser;
import com.lqr.paperragserver.agent.planning.AgentFallbackPolicy;
import com.lqr.paperragserver.agent.planning.AgentPlanner;
import com.lqr.paperragserver.agent.planning.AgentPromptFactory;
import com.lqr.paperragserver.agent.tool.AgentToolRegistry;
import com.lqr.paperragserver.ai.service.LlmService;
import com.lqr.paperragserver.ai.service.PromptConstructionService;
import com.lqr.paperragserver.literature.model.LiteratureSearchContext;
import com.lqr.paperragserver.literature.model.LiteratureSearchResult;
import com.lqr.paperragserver.literature.support.LiteratureSearchIntentParser;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgentPlanner 的规划测试，覆盖动作选择、参数归一化、降级决策和流式最终回答。
 */
class AgentPlannerTest {

    @Test
    void decideShouldPreferExplicitLiteratureLimitAndDateSort() {
        AgentPlanner planner = planner(
                (StubLlmService) prompt -> "{\"thoughtSummary\":\"搜索外部文献。\",\"action\":\"literature_search\",\"actionInput\":{\"query\":\"RAG\",\"limit\":5,\"sortBy\":\"relevance\"},\"finish\":false,\"answer\":null}"
        );

        AgentDecision decision = planner.decide("搜索最近的1篇关于RAG的文献", List.of(), List.of(), List.of(), null);

        assertThat(decision.action()).isEqualTo(AgentActionType.LITERATURE_SEARCH);
        assertThat(decision.actionInput()).containsEntry("query", "RAG");
        assertThat(decision.actionInput()).containsEntry("limit", 1);
        assertThat(decision.actionInput()).containsEntry("sortBy", "date");
    }

    @Test
    void decideShouldNotApplyTopKToLiteratureSearch() {
        AgentPlanner planner = planner(
                (StubLlmService) prompt -> "{\"thoughtSummary\":\"搜索外部文献。\",\"action\":\"literature_search\",\"actionInput\":{\"query\":\"RAG\",\"topK\":3,\"limit\":5},\"finish\":false,\"answer\":null}"
        );

        AgentDecision decision = planner.decide("搜索关于RAG的最新文献", List.of(), List.of(), List.of(), 3);

        assertThat(decision.action()).isEqualTo(AgentActionType.LITERATURE_SEARCH);
        assertThat(decision.actionInput()).doesNotContainKey("topK");
        assertThat(decision.actionInput()).containsEntry("limit", 5);
    }

    @Test
    void decideShouldApplyTopKOnlyToLocalPaperRetrieval() {
        AgentPlanner planner = planner(
                (StubLlmService) prompt -> "{\"thoughtSummary\":\"检索本地论文。\",\"action\":\"local_paper_retrieval\",\"actionInput\":{\"query\":\"RAG\"},\"finish\":false,\"answer\":null}"
        );

        AgentDecision decision = planner.decide("总结我上传的RAG论文", List.of(), List.of(), List.of(), 3);

        assertThat(decision.action()).isEqualTo(AgentActionType.LOCAL_PAPER_RETRIEVAL);
        assertThat(decision.actionInput()).containsEntry("topK", 3);
    }

    @Test
    void decideShouldPreferRequestTopKOverModelLocalTopK() {
        AgentPlanner planner = planner(
                (StubLlmService) prompt -> "{\"thoughtSummary\":\"检索本地论文。\",\"action\":\"local_paper_retrieval\",\"actionInput\":{\"query\":\"RAG\",\"topK\":5},\"finish\":false,\"answer\":null}"
        );

        AgentDecision decision = planner.decide("总结我上传的RAG论文", List.of(), List.of(), List.of(), 10);

        assertThat(decision.action()).isEqualTo(AgentActionType.LOCAL_PAPER_RETRIEVAL);
        assertThat(decision.actionInput()).containsEntry("topK", 10);
    }

    @Test
    void fallbackLiteratureSearchShouldNotCarryTopK() {
        AgentPlanner planner = planner(
                (StubLlmService) prompt -> {
                    throw new RuntimeException("quota exhausted");
                }
        );

        AgentDecision decision = planner.decide("搜索关于RAG的最新文献", List.of(), List.of(), List.of(), 3);

        assertThat(decision.action()).isEqualTo(AgentActionType.LITERATURE_SEARCH);
        assertThat(decision.actionInput()).doesNotContainKey("topK");
        assertThat(decision.actionInput()).containsEntry("limit", 5);
    }

    @Test
    void fallbackLocalRetrievalShouldCarryTopK() {
        AgentPlanner planner = planner(
                (StubLlmService) prompt -> {
                    throw new RuntimeException("quota exhausted");
                }
        );

        AgentDecision decision = planner.decide("总结我上传的RAG论文", List.of(), List.of(), List.of(), 3);

        assertThat(decision.action()).isEqualTo(AgentActionType.LOCAL_PAPER_RETRIEVAL);
        assertThat(decision.actionInput()).containsEntry("topK", 3);
    }

    @Test
    void decideShouldFinishWithExistingObservationsWhenModelFails() {
        AgentPlanner planner = planner(
                (StubLlmService) prompt -> {
                    throw new RuntimeException("quota exhausted");
                }
        );

        AgentDecision decision = planner.decide("搜索 MCP 文献", List.of(), List.of(), List.of("[外部文献] MCP paper"), null);

        assertThat(decision.finish()).isTrue();
        assertThat(decision.action()).isEqualTo(AgentActionType.FINISH);
        assertThat(decision.answer()).contains("MCP paper");
    }

    @Test
    void finalAnswerShouldFallbackToObservationsWhenModelFails() {
        AgentPlanner planner = planner(
                (StubLlmService) prompt -> {
                    throw new RuntimeException("quota exhausted");
                }
        );

        String answer = planner.finalAnswer("搜索 MCP 文献", List.of(), List.of(), List.of("[外部文献] MCP paper"));

        assertThat(answer).contains("MCP paper");
    }

    @Test
    void finalAnswerStreamShouldReturnModelDeltas() {
        AgentPlanner planner = planner(
                new StubLlmService() {
                    @Override
                    public String generate(PromptConstructionService.Prompt prompt) {
                        return "sync answer";
                    }

                    @Override
                    public Flux<String> streamGenerate(PromptConstructionService.Prompt prompt) {
                        return Flux.just("hello ", "world");
                    }
                }
        );

        List<String> deltas = planner.finalAnswerStream("问题", List.of(), List.of(), List.of("证据"))
                .collectList()
                .block();

        assertThat(deltas).containsExactly("hello ", "world");
    }

    @Test
    void decideShouldInheritLiteratureQueryForYearFollowUp() {
        AgentPlanner planner = planner(
                (StubLlmService) prompt -> "{\"thoughtSummary\":\"搜索外部文献。\",\"action\":\"literature_search\",\"actionInput\":{\"query\":\"有没 2026 年的\",\"limit\":5},\"finish\":false,\"answer\":null}"
        );

        AgentDecision decision = planner.decide("有没 2026 年的", List.of(), literatureContext("RAG", 1, List.of()), List.of(), List.of(), null);

        assertThat(decision.action()).isEqualTo(AgentActionType.LITERATURE_SEARCH);
        assertThat(decision.actionInput()).containsEntry("query", "RAG");
        assertThat(decision.actionInput()).containsEntry("limit", 1);
        assertThat(decision.actionInput()).containsEntry("sortBy", "date");
        assertThat(decision.actionInput()).containsEntry("dateFrom", "2026-01-01");
        assertThat(decision.actionInput()).containsEntry("dateTo", "2026-12-31");
    }

    @Test
    void decideShouldInheritLiteratureQueryForLatestFollowUp() {
        AgentPlanner planner = planner(
                (StubLlmService) prompt -> "{\"thoughtSummary\":\"搜索外部文献。\",\"action\":\"literature_search\",\"actionInput\":{\"query\":\"最新的\"},\"finish\":false,\"answer\":null}"
        );

        AgentDecision decision = planner.decide("最新的", List.of(), literatureContext("RAG", 5, List.of()), List.of(), List.of(), null);

        assertThat(decision.actionInput()).containsEntry("query", "RAG");
        assertThat(decision.actionInput()).containsEntry("sortBy", "date");
    }

    @Test
    void decideShouldInheritLiteratureQueryAndOverrideLimitForMoreResults() {
        AgentPlanner planner = planner(
                (StubLlmService) prompt -> "{\"thoughtSummary\":\"搜索外部文献。\",\"action\":\"literature_search\",\"actionInput\":{\"query\":\"再找 3 篇\",\"limit\":5},\"finish\":false,\"answer\":null}"
        );

        AgentDecision decision = planner.decide("再找 3 篇", List.of(), literatureContext("RAG", 5, List.of()), List.of(), List.of(), null);

        assertThat(decision.actionInput()).containsEntry("query", "RAG");
        assertThat(decision.actionInput()).containsEntry("limit", 3);
    }

    @Test
    void decideShouldFinishWhenPreviousLiteratureItemsMatchYearFilter() {
        AgentPlanner planner = planner(
                (StubLlmService) prompt -> {
                    throw new AssertionError("不应调用模型");
                }
        );
        LiteratureSearchResult matching = literatureResult("RAG 2026", 2026, "2026-03-01");
        LiteratureSearchResult other = literatureResult("RAG 2025", 2025, "2025-03-01");

        AgentDecision decision = planner.decide("这些里面有 2026 年的吗", List.of(), literatureContext("RAG", 5, List.of(matching, other)), List.of(), List.of(), null);

        assertThat(decision.finish()).isTrue();
        assertThat(decision.action()).isEqualTo(AgentActionType.FINISH);
        assertThat(decision.answer()).contains("RAG 2026").doesNotContain("RAG 2025");
    }

    private AgentPlanner planner(StubLlmService llmService) {
        LiteratureSearchIntentParser intentParser = new LiteratureSearchIntentParser();
        LiteratureContextPolicy contextPolicy = new LiteratureContextPolicy(intentParser);
        return new AgentPlanner(
                llmService,
                new AgentPromptFactory(new AgentToolRegistry(List.of())),
                new AgentDecisionParser(new ObjectMapper(), contextPolicy),
                new AgentFallbackPolicy(new LiteratureFollowUpPolicy(contextPolicy)),
                contextPolicy
        );
    }

    private LiteratureSearchContext literatureContext(String query, int limit, List<LiteratureSearchResult> items) {
        return new LiteratureSearchContext(
                query,
                limit,
                "relevance",
                null,
                null,
                List.of(),
                items,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null
        );
    }

    private LiteratureSearchResult literatureResult(String title, int year, String publishedDate) {
        return new LiteratureSearchResult(
                title,
                List.of("Alice"),
                "Abstract",
                year,
                publishedDate,
                null,
                List.of("AI"),
                "AI",
                "https://doi.org/test",
                "https://example.org/" + title.replace(' ', '-'),
                null,
                "openalex",
                "W" + year
        );
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