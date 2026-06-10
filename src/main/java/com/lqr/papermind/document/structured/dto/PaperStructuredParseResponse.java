package com.lqr.papermind.document.structured.dto;

import com.lqr.papermind.document.structured.entity.PaperStructuredParseEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 论文结构化解析完整响应。
 */
public record PaperStructuredParseResponse(
        UUID id,
        UUID documentId,
        String sourceId,
        String status,
        Object ruleResult,
        Object modelResult,
        Object mergedResult,
        Object fieldConfidence,
        List<String> missingFields,
        List<String> lowConfidenceFields,
        String rawModelOutput,
        String parserVersion,
        String modelVersion,
        String promptVersion,
        Object qualityMetrics,
        String errorMessage,
        OffsetDateTime parsedAt,
        OffsetDateTime updatedAt
) {
    public static PaperStructuredParseResponse from(PaperStructuredParseEntity entity) {
        if (entity == null) {
            return null;
        }
        return new PaperStructuredParseResponse(
                entity.getId(),
                entity.getDocumentId(),
                entity.getSourceId(),
                entity.getStatus(),
                entity.getRuleResult(),
                entity.getModelResult(),
                entity.getMergedResult(),
                entity.getFieldConfidence(),
                stringList(entity.getMissingFields()),
                stringList(entity.getLowConfidenceFields()),
                entity.getRawModelOutput(),
                entity.getParserVersion(),
                entity.getModelVersion(),
                entity.getPromptVersion(),
                entity.getQualityMetrics(),
                entity.getErrorMessage(),
                entity.getParsedAt(),
                entity.getUpdatedAt()
        );
    }

    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}