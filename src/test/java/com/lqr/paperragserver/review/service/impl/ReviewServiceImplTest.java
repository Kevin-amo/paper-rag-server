package com.lqr.paperragserver.review.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewServiceImplTest {

    private final ReviewServiceImpl service = new ReviewServiceImpl(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new ObjectMapper()
    );

    @Test
    void parseModelOutputShouldAcceptMarkdownWrappedJsonWithTrailingCommas() throws Exception {
        Map<String, Object> parsed = parseModelOutput("""
                下面是评审结果：
                ```json
                {
                  "paperSections": {"title": "论文A",},
                  "scores": [{"code": "LOGIC", "name": "逻辑性", "score": 80, "maxScore": 100,}],
                  "comments": {"summary": "整体可读",},
                  "risks": [],
                  "totalScore": 80,
                  "finalRecommendation": "建议修改后通过",
                }
                ```
                请查收。
                """);

        assertThat(parsed.get("paperSections")).isInstanceOf(Map.class);
        assertThat(parsed.get("scores")).isInstanceOf(List.class);
        assertThat(parsed.get("totalScore")).isEqualTo(80);
        assertThat(parsed.get("finalRecommendation")).isEqualTo("建议修改后通过");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseModelOutput(String value) throws Exception {
        Method method = ReviewServiceImpl.class.getDeclaredMethod("parseModelOutput", String.class);
        method.setAccessible(true);
        return (Map<String, Object>) method.invoke(service, value);
    }
}