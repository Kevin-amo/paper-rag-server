package com.lqr.papermind.agent.tool;

import com.lqr.papermind.agent.paper.CitationFilter;
import com.lqr.papermind.common.model.AnswerCitation;
import com.lqr.papermind.common.model.DocumentChunk;
import com.lqr.papermind.common.model.RetrievedChunk;
import com.lqr.papermind.rag.config.RagProperties;
import com.lqr.papermind.rag.service.RagRetrievalService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalPaperRetrievalAgentToolTest {

    private final RagRetrievalService ragRetrievalService = mock(RagRetrievalService.class);
    private final LocalPaperRetrievalAgentTool tool = new LocalPaperRetrievalAgentTool(
            ragRetrievalService,
            new RagProperties(800, 120, 8, 0),
            new CitationFilter()
    );

    /**
     * 验证本地论文检索工具会过滤纯章节标题，只保留可展示正文片段作为引用。
     */
    @Test
    void executeShouldFilterStandaloneSectionTitlesFromCitations() {
        UUID ownerUserId = UUID.randomUUID();
        when(ragRetrievalService.retrieve(eq(ownerUserId), eq("field"), anyInt())).thenReturn(List.of(
                retrieved("chunk-abstract", 0, "Abstract", 1.0),
                retrieved("chunk-uppercase-abstract", 1, "ABSTRACT", 0.9),
                retrieved("chunk-introduction", 2, "1 Introduction", 0.8),
                retrieved("chunk-references", 3, "References", 0.7),
                retrieved("chunk-body", 4, "Retrieval augmented generation is widely used in information retrieval.", 0.6)
        ));

        AgentToolResult result = tool.execute(ownerUserId, Map.of("query", "field"));

        assertThat(result.citations()).extracting(AnswerCitation::excerpt)
                .doesNotContain("Abstract", "ABSTRACT", "1 Introduction", "References")
                .containsExactly("Retrieval augmented generation is widely used in information retrieval.");
    }

    /**
     * 构造测试用召回片段。
     *
     * @param chunkId   片段标识
     * @param index     片段序号
     * @param content   片段内容
     * @param rankScore 排序分数
     * @return 测试用召回片段
     */
    private RetrievedChunk retrieved(String chunkId, int index, String content, double rankScore) {
        return new RetrievedChunk(
                new DocumentChunk(chunkId, "source-1", index, content, Map.of("title", "Paper A")),
                rankScore
        );
    }
}