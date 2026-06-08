package com.lqr.paperragserver.review.risk;

import org.junit.jupiter.api.Test;

import java.time.Year;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenceFormatCheckerTest {
    private final ReferenceFormatChecker checker = new ReferenceFormatChecker();

    @Test
    void checkShouldReportMissingYearAndOutdatedReferences() {
        var risks = checker.check("""
                [1] Wang A. Intelligent review system research. Journal A, 2010.
                [2] Li X. Paper Review with AI. Conference on AI, 2011.
                [3] Zhang B. Reference without publication year. Publisher.
                """);
        assertThat(risks).extracting(ReferenceFormatChecker.ReferenceRisk::riskType)
                .contains("REFERENCE_FORMAT", "REFERENCE_OUTDATED");
    }

    @Test
    void checkShouldReturnEmptyForBlankInput() {
        assertThat(checker.check("   ")).isEmpty();
    }

    @Test
    void checkShouldReportMissingYearInUnnumberedReferences() {
        var risks = checker.check("""
                Wang A. Intelligent review system research. Journal A, 2018.
                Li X. Paper Review with AI. Conference on AI, 2019.
                Zhang B. Reference without publication year. Publisher.
                """);

        assertThat(risks).extracting(ReferenceFormatChecker.ReferenceRisk::riskType)
                .contains("REFERENCE_FORMAT");
    }

    @Test
    void checkShouldMergeMultilineNumberedReference() {
        var risks = checker.check("""
                [1] Wang A. A long reference title that wraps to another line
                Journal of Review Systems, 2024.
                """);

        assertThat(risks).isEmpty();
    }

    @Test
    void checkShouldNotReportOutdatedWhenOnlyReferencesAreMissingYear() {
        var risks = checker.check("""
                Wang A. Reference without publication year. Journal A.
                Li X. Another reference without publication year. Conference B.
                """);

        assertThat(risks).extracting(ReferenceFormatChecker.ReferenceRisk::riskType)
                .doesNotContain("REFERENCE_OUTDATED");
    }

    @Test
    void checkShouldTreatCurrentYearMinusFiveAsRecent() {
        int boundaryYear = Year.now().getValue() - 5;

        var risks = checker.check("""
                [1] Wang A. Boundary reference. Journal A, %d.
                [2] Li X. Older reference. Conference B, 2010.
                """.formatted(boundaryYear));

        assertThat(risks).extracting(ReferenceFormatChecker.ReferenceRisk::riskType)
                .doesNotContain("REFERENCE_OUTDATED");
    }

    @Test
    void checkShouldNotReportOutdatedWhenRecentReferenceExists() {
        int recentYear = Year.now().getValue() - 1;

        var risks = checker.check("""
                [1] Wang A. Recent reference. Journal A, %d.
                [2] Li X. Older reference. Conference B, 2010.
                """.formatted(recentYear));

        assertThat(risks).extracting(ReferenceFormatChecker.ReferenceRisk::riskType)
                .doesNotContain("REFERENCE_OUTDATED");
    }

    @Test
    void checkShouldIgnoreHeadingAndProseLines() {
        var risks = checker.check("""
                References

                The following references support the review.
                [1] Wang A. Recent reference. Journal A, 2024.
                """);

        assertThat(risks).isEmpty();
    }

    @Test
    void checkShouldIgnoreChineseReferenceHeading() {
        var risks = checker.check("""
                \u53C2\u8003\u6587\u732E
                [1] Wang A. Recent reference. Journal A, 2024.
                """);

        assertThat(risks).isEmpty();
    }
}
