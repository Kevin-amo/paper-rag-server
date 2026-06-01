package com.lqr.paperragserver.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.paperragserver.agent.core.AgentActionType;
import com.lqr.paperragserver.agent.core.AgentDecision;
import com.lqr.paperragserver.agent.core.AgentStep;
import com.lqr.paperragserver.agent.paper.LiteratureContextPolicy;
import com.lqr.paperragserver.agent.paper.LiteratureFollowUpPolicy;
import com.lqr.paperragserver.agent.planning.AgentDecisionParser;
import com.lqr.paperragserver.agent.planning.AgentFallbackPolicy;
import com.lqr.paperragserver.agent.planning.AgentHybridTaskPolicy;
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

    /**
     * 验证显式文献数量和最新排序意图会覆盖模型原始参数。
     */
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

    /**
     * 验证外部文献搜索动作不会携带本地检索 topK 参数。
     */
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

    /**
     * 验证 topK 只会应用到本地论文检索动作。
     */
    @Test
    void decideShouldApplyTopKOnlyToLocalPaperRetrieval() {
        AgentPlanner planner = planner(
                (StubLlmService) prompt -> "{\"thoughtSummary\":\"检索本地论文。\",\"action\":\"local_paper_retrieval\",\"actionInput\":{\"query\":\"RAG\"},\"finish\":false,\"answer\":null}"
        );

        AgentDecision decision = planner.decide("总结我上传的RAG论文", List.of(), List.of(), List.of(), 3);

        assertThat(decision.action()).isEqualTo(AgentActionType.LOCAL_PAPER_RETRIEVAL);
        assertThat(decision.actionInput()).containsEntry("topK", 3);
    }

    /**
     * 验证请求参数中的 topK 会覆盖模型输出里的本地检索 topK。
     */
    @Test
    void decideShouldPreferRequestTopKOverModelLocalTopK() {
        AgentPlanner planner = planner(
                (StubLlmService) prompt -> "{\"thoughtSummary\":\"检索本地论文。\",\"action\":\"local_paper_retrieval\",\"actionInput\":{\"query\":\"RAG\",\"topK\":5},\"finish\":false,\"answer\":null}"
        );

        AgentDecision decision = planner.decide("总结我上传的RAG论文", List.of(), List.of(), List.of(), 10);

        assertThat(decision.action()).isEqualTo(AgentActionType.LOCAL_PAPER_RETRIEVAL);
        assertThat(decision.actionInput()).containsEntry("topK", 10);
    }

    /**
     * 验证规划模型失败时，文献搜索兜底决策不会携带 topK。
     */
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

    /**
     * 验证规划模型失败时，本地检索兜底决策会携带 topK。
     */
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

    /**
     * 验证已有工具观察且模型失败时，会直接生成结束决策。
     */
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

    /**
     * 验证最终回答模型失败时，会回退到工具观察内容。
     */
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

    /**
     * 验证流式最终回答会透传模型输出的增量文本。
     */
    @Test
    void finalAnswerStreamShouldReturnModelDeltas() {
        AgentPlanner planner = planner(
                new StubLlmService() {
                    /**
                     * 返回同步模型固定输出。
                     *
                     * @param prompt 提示词
                     * @return 模型输出文本
                     */
                    @Override
                    public String generate(PromptConstructionService.Prompt prompt) {
                        return "sync answer";
                    }

                    /**
                     * 返回测试指定的流式模型增量。
                     *
                     * @param prompt 提示词
                     * @return 模型输出增量流
                     */
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

    /**
     * 验证年份追问会继承上一轮文献搜索 query，并补齐年份过滤条件。
     */
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

    /**
     * 验证“最新的”这类追问会继承上一轮文献搜索 query 并切换为日期排序。
     */
    @Test
    void decideShouldInheritLiteratureQueryForLatestFollowUp() {
        AgentPlanner planner = planner(
                (StubLlmService) prompt -> "{\"thoughtSummary\":\"搜索外部文献。\",\"action\":\"literature_search\",\"actionInput\":{\"query\":\"最新的\"},\"finish\":false,\"answer\":null}"
        );

        AgentDecision decision = planner.decide("最新的", List.of(), literatureContext("RAG", 5, List.of()), List.of(), List.of(), null);

        assertThat(decision.actionInput()).containsEntry("query", "RAG");
        assertThat(decision.actionInput()).containsEntry("sortBy", "date");
    }

    /**
     * 验证“再找几篇”这类追问会继承 query 并覆盖结果数量。
     */
    @Test
    void decideShouldInheritLiteratureQueryAndOverrideLimitForMoreResults() {
        AgentPlanner planner = planner(
                (StubLlmService) prompt -> "{\"thoughtSummary\":\"搜索外部文献。\",\"action\":\"literature_search\",\"actionInput\":{\"query\":\"再找 3 篇\",\"limit\":5},\"finish\":false,\"answer\":null}"
        );

        AgentDecision decision = planner.decide("再找 3 篇", List.of(), literatureContext("RAG", 5, List.of()), List.of(), List.of(), null);

        assertThat(decision.actionInput()).containsEntry("query", "RAG");
        assertThat(decision.actionInput()).containsEntry("limit", 3);
    }

    /**
     * 验证上一轮文献结果已经满足年份筛选时，会直接结束而不再调用模型。
     */
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

    /**
     * 验证复合任务会跳过模型自由决策，按外部文献、本地知识库、结束的顺序执行。
     */
    @Test
    void decideShouldUseDeterministicHybridSequence() {
        AgentPlanner planner = planner(
                (StubLlmService) prompt -> {
                    throw new AssertionError("复合任务不应依赖模型决策");
                }
        );
        String question = "帮我找 Graph RAG 最新论文，并结合我的知识库总结趋势";

        AgentDecision literatureDecision = planner.decide(question, List.of(), List.of(), List.of(), 7);
        AgentStep literatureStep = new AgentStep(1, "搜索外部文献", "literature_search", literatureDecision.actionInput(), "找到 0 篇论文。");
        AgentDecision localDecision = planner.decide(question, List.of(), List.of(literatureStep), List.of("未找到外部文献结果。"), 7);
        AgentStep localStep = new AgentStep(2, "检索本地知识库", "local_paper_retrieval", localDecision.actionInput(), "找到 2 个相关片段。");
        AgentDecision finishDecision = planner.decide(question, List.of(), List.of(literatureStep, localStep), List.of("未找到外部文献结果。", "本地论文证据"), 7);

        assertThat(literatureDecision.action()).isEqualTo(AgentActionType.LITERATURE_SEARCH);
        assertThat(literatureDecision.actionInput()).containsEntry("query", "Graph RAG");
        assertThat(literatureDecision.actionInput()).containsEntry("sortBy", "date");
        assertThat(literatureDecision.actionInput()).containsEntry("limit", 5);
        assertThat(localDecision.action()).isEqualTo(AgentActionType.LOCAL_PAPER_RETRIEVAL);
        assertThat(localDecision.actionInput()).containsEntry("query", "Graph RAG 研究趋势");
        assertThat(localDecision.actionInput()).containsEntry("topK", 7);
        assertThat(finishDecision.finish()).isTrue();
        assertThat(finishDecision.action()).isEqualTo(AgentActionType.FINISH);
    }

    /**
     * 构造带固定模型服务的规划器测试对象。
     *
     * @param llmService 测试用模型服务
     * @return 规划器实例
     */
    private AgentPlanner planner(StubLlmService llmService) {
        LiteratureSearchIntentParser intentParser = new LiteratureSearchIntentParser();
        LiteratureContextPolicy contextPolicy = new LiteratureContextPolicy(intentParser);
        return new AgentPlanner(
                llmService,
                new AgentPromptFactory(new AgentToolRegistry(List.of())),
                new AgentDecisionParser(new ObjectMapper(), contextPolicy),
                new AgentFallbackPolicy(new LiteratureFollowUpPolicy(contextPolicy), new AgentHybridTaskPolicy(intentParser)),
                contextPolicy,
                new AgentHybridTaskPolicy(intentParser)
        );
    }

    /**
     * 构造最近一次文献搜索上下文。
     *
     * @param query 搜索 query
     * @param limit 结果数量上限
     * @param items 文献结果列表
     * @return 文献搜索上下文
     */
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

    /**
     * 构造测试用文献搜索结果。
     *
     * @param title         文献标题
     * @param year          发布年份
     * @param publishedDate 发布日期
     * @return 文献搜索结果
     */
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
        /**
         * 返回同步模型固定输出。
         *
         * @param prompt 提示词
         * @return 模型输出文本
         */
        @Override
        String generate(PromptConstructionService.Prompt prompt);

        /**
         * 返回空的流式模型输出，测试需要时可由匿名类覆盖。
         *
         * @param prompt 提示词
         * @return 空增量流
         */
        @Override
        default Flux<String> streamGenerate(PromptConstructionService.Prompt prompt) {
            return Flux.empty();
        }

        /**
         * 复用同步模型固定输出作为带工具模型输出。
         *
         * @param prompt 提示词
         * @return 模型输出文本
         */
        @Override
        default String generateWithTools(PromptConstructionService.Prompt prompt) {
            return generate(prompt);
        }

        /**
         * 返回空的带工具流式模型输出。
         *
         * @param prompt 提示词
         * @return 空增量流
         */
        @Override
        default Flux<String> streamGenerateWithTools(PromptConstructionService.Prompt prompt) {
            return Flux.empty();
        }
    }
}