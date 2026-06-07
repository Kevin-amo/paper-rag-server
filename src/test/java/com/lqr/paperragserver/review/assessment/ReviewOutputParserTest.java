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
}
