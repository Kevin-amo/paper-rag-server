package com.lqr.papermind.review.consensus;

import com.lqr.papermind.review.entity.ReviewReportEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class ConsensusCalculator {

    private static final int OVERALL_DISAGREEMENT_THRESHOLD = 15;
    private static final int CRITERION_DISAGREEMENT_THRESHOLD = 20;

    /**
     * 共识计算结果记录。
     *
     * @param scoreSummary     分数摘要（包含 overallAverage、criteria 等）
     * @param commentSummary   评审意见摘要（包含 recommendations）
     * @param disagreementItems 分歧项列表
     * @param finalScore       最终共识分数
     */
    public record Result(Map<String, Object> scoreSummary,
                         Map<String, Object> commentSummary,
                         List<Map<String, Object>> disagreementItems,
                         Integer finalScore) {
    }

    /**
     * 对多份评审报告进行共识计算。
     * 汇总总分平均值、各评审标准分数、评审意见，以及检测评分分歧。
     *
     * @param reports 评审报告列表
     * @return 共识计算结果
     */
    public Result calculate(List<ReviewReportEntity> reports) {
        List<ReviewReportEntity> safeReports = reports == null
                ? List.of()
                : reports.stream().filter(Objects::nonNull).toList();
        List<Integer> overallScores = safeReports.stream()
                .map(ReviewReportEntity::getTotalScore)
                .filter(Objects::nonNull)
                .toList();

        int overallAverage = roundedAverage(overallScores);
        Map<String, Object> scoreSummary = new LinkedHashMap<>();
        scoreSummary.put("overallAverage", overallAverage);
        scoreSummary.put("participantCount", safeReports.size());

        List<Map<String, Object>> disagreementItems = new ArrayList<>();
        if (!overallScores.isEmpty()) {
            int min = overallScores.stream().mapToInt(Integer::intValue).min().orElse(0);
            int max = overallScores.stream().mapToInt(Integer::intValue).max().orElse(0);
            scoreSummary.put("overallMin", min);
            scoreSummary.put("overallMax", max);
            if (max - min >= OVERALL_DISAGREEMENT_THRESHOLD) {
                disagreementItems.add(disagreement("OVERALL_SCORE", null, min, max, OVERALL_DISAGREEMENT_THRESHOLD));
            }
        }

        List<Map<String, Object>> criteria = summarizeCriteria(safeReports, disagreementItems);
        scoreSummary.put("criteria", criteria);

        Map<String, Object> commentSummary = new LinkedHashMap<>();
        commentSummary.put("recommendations", recommendations(safeReports));

        return new Result(scoreSummary, commentSummary, disagreementItems, overallAverage);
    }

    /**
     * 汇总各评审标准的分数统计信息，并检测标准级别的评分分歧。
     *
     * @param reports           评审报告列表
     * @param disagreementItems 分歧项列表（会将检测到的标准分歧追加到此列表）
     * @return 各评审标准的摘要列表（包含 criterionCode、average、minScore、maxScore、participantCount）
     */
    private List<Map<String, Object>> summarizeCriteria(List<ReviewReportEntity> reports,
                                                        List<Map<String, Object>> disagreementItems) {
        Map<String, List<Integer>> scoresByCode = new LinkedHashMap<>();
        for (ReviewReportEntity report : reports) {
            if (report == null) {
                continue;
            }
            if (!(report.getScores() instanceof List<?> scores)) {
                continue;
            }
            for (Object item : scores) {
                if (!(item instanceof Map<?, ?> scoreItem)) {
                    continue;
                }
                Object code = scoreItem.get("code");
                Integer score = numericScore(scoreItem.get("score"));
                if (code == null || score == null) {
                    continue;
                }
                scoresByCode.computeIfAbsent(String.valueOf(code), ignored -> new ArrayList<>()).add(score);
            }
        }

        List<Map<String, Object>> criteria = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> entry : scoresByCode.entrySet()) {
            List<Integer> scores = entry.getValue();
            int min = scores.stream().mapToInt(Integer::intValue).min().orElse(0);
            int max = scores.stream().mapToInt(Integer::intValue).max().orElse(0);

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("criterionCode", entry.getKey());
            summary.put("average", roundedAverage(scores));
            summary.put("minScore", min);
            summary.put("maxScore", max);
            summary.put("participantCount", scores.size());
            criteria.add(summary);

            if (max - min >= CRITERION_DISAGREEMENT_THRESHOLD) {
                disagreementItems.add(disagreement("CRITERION_SCORE", entry.getKey(), min, max, CRITERION_DISAGREEMENT_THRESHOLD));
            }
        }
        return criteria;
    }

    /**
     * 从各评审报告中提取最终评审建议。
     *
     * @param reports 评审报告列表
     * @return 包含 reportId、reviewerUserId、finalRecommendation 的建议列表
     */
    private List<Map<String, Object>> recommendations(List<ReviewReportEntity> reports) {
        List<Map<String, Object>> recommendations = new ArrayList<>();
        for (ReviewReportEntity report : reports) {
            if (report == null) {
                continue;
            }
            Map<String, Object> recommendation = new LinkedHashMap<>();
            recommendation.put("reportId", report.getId());
            recommendation.put("reviewerUserId", report.getReviewerUserId());
            recommendation.put("finalRecommendation", report.getFinalRecommendation() == null ? "" : report.getFinalRecommendation());
            recommendations.add(recommendation);
        }
        return recommendations;
    }

    /**
     * 构建一个分歧项描述。
     *
     * @param type         分歧类型（OVERALL_SCORE 或 CRITERION_SCORE）
     * @param criterionCode 评审标准代码（总分分歧时为 null）
     * @param minScore     最低分
     * @param maxScore     最高分
     * @param threshold    分歧阈值
     * @return 分歧项映射
     */
    private Map<String, Object> disagreement(String type, String criterionCode, int minScore, int maxScore, int threshold) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", type);
        if (criterionCode != null) {
            item.put("criterionCode", criterionCode);
        }
        item.put("minScore", minScore);
        item.put("maxScore", maxScore);
        item.put("threshold", threshold);
        item.put("message", type + " disagreement: score range " + (maxScore - minScore) + " reached threshold " + threshold);
        return item;
    }

    /**
     * 将任意值安全转换为整数分数。支持 Number 和可解析的字符串。
     *
     * @param value 原始值
     * @return 转换后的整数分数，无法解析时返回 null
     */
    private Integer numericScore(Object value) {
        if (value instanceof Number number) {
            return (int) Math.round(number.doubleValue());
        }
        if (value instanceof String text) {
            try {
                return (int) Math.round(Double.parseDouble(text.trim()));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 计算整数列表的四舍五入平均值。
     *
     * @param scores 整数分数列表
     * @return 四舍五入后的平均值，列表为空时返回 0
     */
    private int roundedAverage(List<Integer> scores) {
        if (scores.isEmpty()) {
            return 0;
        }
        return (int) Math.round(scores.stream().mapToInt(Integer::intValue).average().orElse(0));
    }
}
