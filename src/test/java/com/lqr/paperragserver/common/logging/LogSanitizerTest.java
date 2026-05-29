package com.lqr.paperragserver.common.logging;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LogSanitizerTest {

    @Test
    void safeExcerptShouldNormalizeWhitespaceAndTruncate() {
        assertThat(LogSanitizer.safeExcerpt("  hello\n\tworld  ", 20)).isEqualTo("hello world");
        assertThat(LogSanitizer.safeExcerpt("abcdefghijklmnopqrstuvwxyz", 10)).isEqualTo("abcdefg...");
        assertThat(LogSanitizer.safeExcerpt(null, 10)).isEmpty();
    }

    @Test
    void safeActionInputShouldKeepOnlySafeSummary() {
        Map<String, Object> summary = LogSanitizer.safeActionInput(Map.of(
                "query", "first line\nsecond line with long text",
                "topK", 3,
                "Authorization", "secret",
                "password", "secret"
        ));

        assertThat(summary).containsEntry("queryLength", 37);
        assertThat(summary).containsEntry("queryExcerpt", "first line second line with long text");
        assertThat(summary).containsEntry("topK", "3");
        assertThat(summary).doesNotContainKeys("Authorization", "password");
        assertThat(summary.get("keys").toString()).contains("Authorization", "password", "query", "topK");
    }

    @Test
    void safeUriSummaryShouldHideQueryValues() {
        Map<String, Object> summary = LogSanitizer.safeUriSummary(URI.create(
                "https://api.openalex.org/works?search=rag&mailto=user@example.com&token=secret"
        ));

        assertThat(summary).containsEntry("scheme", "https");
        assertThat(summary).containsEntry("host", "api.openalex.org");
        assertThat(summary).containsEntry("path", "/works");
        assertThat(summary).containsEntry("hasQuery", true);
        assertThat(summary.get("queryKeys").toString()).contains("search", "mailto", "token");
        assertThat(summary.toString()).doesNotContain("user@example.com", "secret");
    }
}