package com.lqr.paperragserver.review.risk;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenceFormatCheckerTest {
    private final ReferenceFormatChecker checker = new ReferenceFormatChecker();

    @Test
    void checkShouldReportMissingYearAndOutdatedReferences() {
        var risks = checker.check("""
                [1] 王某某. 智能评审系统研究. 计算机应用, 2010.
                [2] Li X. Paper Review with AI. Conference on AI, 2011.
                [3] 张某某. 缺少年份的文献. 某某出版社.
                """);
        assertThat(risks).extracting(ReferenceFormatChecker.ReferenceRisk::riskType)
                .contains("REFERENCE_FORMAT", "REFERENCE_OUTDATED");
    }

    @Test
    void checkShouldReturnEmptyForBlankInput() {
        assertThat(checker.check("   ")).isEmpty();
    }
}