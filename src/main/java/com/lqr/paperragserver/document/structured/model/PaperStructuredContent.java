package com.lqr.paperragserver.document.structured.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 论文结构化解析后的标准内容。
 */
public record PaperStructuredContent(
        String title,
        @JsonProperty("abstract") String abstractText,
        String introduction,
        String literatureReview,
        String methodology,
        String experimentResults,
        String discussion,
        String conclusion,
        String references,
        List<String> keywords,
        String researchObject,
        String researchQuestion,
        List<String> innovationPoints,
        String methodPath,
        String experimentDataSummary,
        List<String> mainConclusions
) {
}