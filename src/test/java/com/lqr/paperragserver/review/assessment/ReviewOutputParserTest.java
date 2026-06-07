package com.lqr.paperragserver.review.assessment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewOutputParserTest {
    private final ReviewOutputParser parser = new ReviewOutputParser(new ObjectMapper());

    @Test
    void parseShouldExtractMarkdownJsonRepairTrailingCommasAndClampScores() {
        Map<String, Object> parsed = parser.parse("""
                ```json
                {
                  "paperSections": {"title": "\u8bba\u6587A",},
                  "scores": [{"code": "LOGIC", "score": 130, "maxScore": 100, "confidence": 1.4,}],
                  "comments": {"summary": "\u53ef\u8bfb",},
                  "risks": [],
                  "totalScore": 120,
                  "finalRecommendation": "\u5efa\u8bae\u4fee\u6539\u540e\u901a\u8fc7",
                }
                ```
                """);
        assertThat(parsed.get("totalScore")).isEqualTo(100);
        List<?> scores = (List<?>) parsed.get("scores");
        Map<?, ?> first = (Map<?, ?>) scores.get(0);
        assertThat(first.get("score")).isEqualTo(100);
        assertThat(first.get("confidence")).isEqualTo(1.0);
    }

    @Test
    void parseShouldRejectTextWithoutJson() {
        assertThatThrownBy(() -> parser.parse("\u6ca1\u6709 JSON"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("\u7f3a\u5c11 JSON \u5bf9\u8c61");
    }

    @Test
    void parseShouldExtractBalancedNestedObjectsAndArrays() {
        Map<String, Object> parsed = parser.parse("""
                prefix {"paperSections":{"nested":{"items":[{"value":1},{"value":2}]}},"scores":[],"comments":{},"risks":[],"totalScore":88} suffix
                """);

        assertThat(parsed.get("totalScore")).isEqualTo(88);
        Map<?, ?> paperSections = (Map<?, ?>) parsed.get("paperSections");
        Map<?, ?> nested = (Map<?, ?>) paperSections.get("nested");
        List<?> items = (List<?>) nested.get("items");
        assertThat(items).hasSize(2);
    }

    @Test
    void parseShouldIgnoreBracesEscapedQuotesAndBackslashesInsideStrings() {
        Map<String, Object> parsed = parser.parse("""
                note {"paperSections":{"title":"brace { text } quote \\\"x\\\" path C:\\\\tmp"},"scores":[],"comments":{},"risks":[],"totalScore":70} tail {ignored}
                """);

        assertThat(parsed.get("totalScore")).isEqualTo(70);
        Map<?, ?> paperSections = (Map<?, ?>) parsed.get("paperSections");
        assertThat(paperSections.get("title")).isEqualTo("brace { text } quote \"x\" path C:\\tmp");
    }

    @Test
    void parseShouldClampScoresWithMissingNonNumberAndStringNumbers() {
        Map<String, Object> parsed = parser.parse("""
                {
                  "scores": [
                    {"code": "MISSING", "score": 120, "confidence": "0.7"},
                    {"code": "TEXT", "score": "12", "maxScore": "10", "confidence": "1.2"},
                    {"code": "BAD", "score": "bad", "maxScore": "bad", "confidence": "bad"}
                  ],
                  "totalScore": 50
                }
                """);

        List<?> scores = (List<?>) parsed.get("scores");
        Map<?, ?> missing = (Map<?, ?>) scores.get(0);
        assertThat(missing.get("maxScore")).isEqualTo(100);
        assertThat(missing.get("score")).isEqualTo(100);
        assertThat(missing.get("confidence")).isEqualTo(0.7);

        Map<?, ?> text = (Map<?, ?>) scores.get(1);
        assertThat(text.get("maxScore")).isEqualTo(10);
        assertThat(text.get("score")).isEqualTo(10);
        assertThat(text.get("confidence")).isEqualTo(1.0);

        Map<?, ?> bad = (Map<?, ?>) scores.get(2);
        assertThat(bad.get("maxScore")).isEqualTo(100);
        assertThat(bad.get("score")).isEqualTo(0);
        assertThat(bad.get("confidence")).isEqualTo(0.0);
    }

    @Test
    void parseShouldRejectInvalidJsonThatCannotBeRepaired() {
        assertThatThrownBy(() -> parser.parse("{\"scores\": [}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("\u4e0d\u662f\u6709\u6548 JSON");
    }

}
