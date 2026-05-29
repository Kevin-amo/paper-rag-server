package com.lqr.paperragserver.literature.support;

import com.lqr.paperragserver.literature.model.LiteratureSearchContext;
import com.lqr.paperragserver.literature.model.LiteratureSearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LiteratureSearchIntentParserTest {

    private final LiteratureSearchIntentParser parser = new LiteratureSearchIntentParser();

    @Test
    void parseShouldInheritQueryAndBuildYearRangeForFollowUp() {
        LiteratureSearchContext context = context("RAG", 1);

        LiteratureSearchIntentParser.Intent intent = parser.parse("有没 2026 年的", context);

        assertThat(intent.query()).isEqualTo("RAG");
        assertThat(intent.dateFrom()).isEqualTo("2026-01-01");
        assertThat(intent.dateTo()).isEqualTo("2026-12-31");
        assertThat(intent.sortBy()).isEqualTo("date");
        assertThat(intent.limit()).isEqualTo(1);
    }

    @Test
    void parseShouldInheritQueryAndSortByDateForLatest() {
        LiteratureSearchContext context = context("RAG", 5);

        LiteratureSearchIntentParser.Intent intent = parser.parse("最新的", context);

        assertThat(intent.query()).isEqualTo("RAG");
        assertThat(intent.sortBy()).isEqualTo("date");
        assertThat(intent.limit()).isEqualTo(5);
    }

    @Test
    void parseShouldInheritQueryAndOverrideLimit() {
        LiteratureSearchContext context = context("RAG", 5);

        LiteratureSearchIntentParser.Intent intent = parser.parse("再找 3 篇", context);

        assertThat(intent.query()).isEqualTo("RAG");
        assertThat(intent.limit()).isEqualTo(3);
    }

    @Test
    void parseShouldUseNewExplicitTopic() {
        LiteratureSearchContext context = context("RAG", 5);

        LiteratureSearchIntentParser.Intent intent = parser.parse("搜索关于 Graph Neural Network 的文献", context);

        assertThat(intent.query()).isEqualTo("Graph Neural Network");
        assertThat(intent.limit()).isEqualTo(5);
    }

    @Test
    void parseShouldMarkPreviousItemsQuestion() {
        LiteratureSearchContext context = context("RAG", 5);

        LiteratureSearchIntentParser.Intent intent = parser.parse("这些里面有 2026 年的吗", context);

        assertThat(intent.query()).isEqualTo("RAG");
        assertThat(intent.withinPreviousItems()).isTrue();
        assertThat(intent.dateFrom()).isEqualTo("2026-01-01");
        assertThat(intent.dateTo()).isEqualTo("2026-12-31");
    }

    private LiteratureSearchContext context(String query, int limit) {
        return new LiteratureSearchContext(
                query,
                limit,
                "relevance",
                null,
                null,
                List.of(),
                List.of(new LiteratureSearchResult("RAG Paper", List.of("Alice"), "Abstract", 2025, "2025-01-01", null, List.of(), null, null, null, null, "openalex", UUID.randomUUID().toString())),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null
        );
    }
}