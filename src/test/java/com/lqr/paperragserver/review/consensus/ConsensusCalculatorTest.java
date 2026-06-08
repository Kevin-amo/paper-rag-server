package com.lqr.paperragserver.review.consensus;

import com.lqr.paperragserver.review.entity.ReviewReportEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ConsensusCalculatorTest {

    private final ConsensusCalculator calculator = new ConsensusCalculator();

    @Test
    void calculateShouldAverageOverallAndCriterionScores() {
        ReviewReportEntity first = report(UUID.randomUUID(), UUID.randomUUID(), 80, "寤鸿閫氳繃",
                List.of(
                        Map.of("code", "LOGIC", "score", 70),
                        Map.of("code", "NOVELTY", "score", "88")
                ));
        ReviewReportEntity second = report(UUID.randomUUID(), UUID.randomUUID(), 90, "寤鸿淇敼鍚庨€氳繃",
                List.of(
                        Map.of("code", "LOGIC", "score", 95),
                        Map.of("code", "NOVELTY", "score", 92)
                ));

        ConsensusCalculator.Result result = calculator.calculate(List.of(first, second));

        assertThat(result.finalScore()).isEqualTo(85);
        assertThat(result.scoreSummary())
                .containsEntry("overallAverage", 85)
                .containsEntry("participantCount", 2)
                .containsEntry("overallMin", 80)
                .containsEntry("overallMax", 90);
        assertThat(result.scoreSummary().get("criteria")).asList()
                .anySatisfy(criterion -> assertThat((Map<String, Object>) criterion)
                        .containsEntry("criterionCode", "LOGIC")
                        .containsEntry("average", 83)
                        .containsEntry("minScore", 70)
                        .containsEntry("maxScore", 95)
                        .containsEntry("participantCount", 2));
        assertThat(result.disagreementItems())
                .anySatisfy(item -> assertThat(item)
                        .containsEntry("type", "CRITERION_SCORE")
                        .containsEntry("criterionCode", "LOGIC")
                        .containsEntry("minScore", 70)
                        .containsEntry("maxScore", 95)
                        .containsEntry("threshold", 20));
        assertThat(result.commentSummary().get("recommendations")).asList()
                .hasSize(2)
                .anySatisfy(recommendation -> assertThat((Map<String, Object>) recommendation)
                        .containsEntry("reportId", first.getId())
                        .containsEntry("reviewerUserId", first.getReviewerUserId())
                        .containsEntry("finalRecommendation", "寤鸿閫氳繃"));
    }

    @Test
    void calculateShouldFlagOverallDisagreementAtThreshold() {
        ReviewReportEntity first = report(UUID.randomUUID(), UUID.randomUUID(), 75, "寤鸿閫氳繃", List.of());
        ReviewReportEntity second = report(UUID.randomUUID(), UUID.randomUUID(), 90, "寤鸿澶嶆牳", List.of());

        ConsensusCalculator.Result result = calculator.calculate(List.of(first, second));

        assertThat(result.finalScore()).isEqualTo(83);
        assertThat(result.disagreementItems())
                .anySatisfy(item -> assertThat(item)
                        .containsEntry("type", "OVERALL_SCORE")
                        .containsEntry("minScore", 75)
                        .containsEntry("maxScore", 90)
                        .containsEntry("threshold", 15));
    }

    @Test
    void calculateShouldReturnZeroForEmptyReports() {
        ConsensusCalculator.Result result = calculator.calculate(List.of());

        assertThat(result.finalScore()).isZero();
        assertThat(result.scoreSummary())
                .containsEntry("overallAverage", 0)
                .containsEntry("participantCount", 0);
        assertThat(result.scoreSummary().get("criteria")).asList().isEmpty();
        assertThat(result.disagreementItems()).isEmpty();
    }

    @Test
    void calculateShouldTreatNullReportsAsEmpty() {
        ConsensusCalculator.Result result = calculator.calculate(null);

        assertThat(result.finalScore()).isZero();
        assertThat(result.scoreSummary())
                .containsEntry("overallAverage", 0)
                .containsEntry("participantCount", 0);
        assertThat(result.scoreSummary().get("criteria")).asList().isEmpty();
        assertThat(result.commentSummary().get("recommendations")).asList().isEmpty();
        assertThat(result.disagreementItems()).isEmpty();
    }

    @Test
    void calculateShouldIgnoreNullReportsAndInvalidScores() {
        ReviewReportEntity valid = report(UUID.randomUUID(), UUID.randomUUID(), 50, null,
                Arrays.asList(
                        "not-a-map",
                        Map.of("code", "LOGIC", "score", "not-a-number"),
                        Map.of("code", "LOGIC", "score", 77)
                ));

        ConsensusCalculator.Result result = calculator.calculate(Arrays.asList(null, valid));

        assertThat(result.finalScore()).isEqualTo(50);
        assertThat(result.scoreSummary())
                .containsEntry("overallAverage", 50)
                .containsEntry("participantCount", 1)
                .containsEntry("overallMin", 50)
                .containsEntry("overallMax", 50);
        assertThat(result.scoreSummary().get("criteria")).asList()
                .anySatisfy(criterion -> assertThat((Map<String, Object>) criterion)
                        .containsEntry("criterionCode", "LOGIC")
                        .containsEntry("average", 77)
                        .containsEntry("minScore", 77)
                        .containsEntry("maxScore", 77)
                        .containsEntry("participantCount", 1));
        assertThat(result.commentSummary().get("recommendations")).asList()
                .hasSize(1)
                .anySatisfy(recommendation -> assertThat((Map<String, Object>) recommendation)
                        .containsEntry("reportId", valid.getId())
                        .containsEntry("reviewerUserId", valid.getReviewerUserId())
                        .containsEntry("finalRecommendation", ""));
        assertThat(result.disagreementItems()).isEmpty();
    }

    private ReviewReportEntity report(UUID id, UUID reviewerUserId, Integer totalScore, String finalRecommendation, Object scores) {
        ReviewReportEntity report = new ReviewReportEntity();
        report.setId(id);
        report.setReviewerUserId(reviewerUserId);
        report.setTotalScore(totalScore);
        report.setFinalRecommendation(finalRecommendation);
        report.setScores(scores);
        return report;
    }
}
