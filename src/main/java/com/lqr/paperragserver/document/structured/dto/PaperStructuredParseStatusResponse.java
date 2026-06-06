package com.lqr.paperragserver.document.structured.dto;

import com.lqr.paperragserver.document.structured.entity.PaperStructuredParseEntity;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 论文结构化解析状态响应。
 */
public record PaperStructuredParseStatusResponse(
        String sourceId,
        String status,
        List<String> missingFields,
        List<String> lowConfidenceFields,
        String errorMessage,
        OffsetDateTime parsedAt,
        OffsetDateTime updatedAt
) {
    public static PaperStructuredParseStatusResponse from(PaperStructuredParseEntity entity) {
        return new PaperStructuredParseStatusResponse(
                entity.getSourceId(),
                entity.getStatus(),
                stringList(entity.getMissingFields()),
                stringList(entity.getLowConfidenceFields()),
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