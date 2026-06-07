package com.lqr.paperragserver.review.risk;

import com.lqr.paperragserver.review.dto.ReviewRiskItemResponse;
import com.lqr.paperragserver.review.entity.ReviewRiskItemEntity;
import com.lqr.paperragserver.review.mapper.ReviewRiskItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewRiskService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;

    private final ReviewRiskItemMapper mapper;

    public List<ReviewRiskItemResponse> listByReportId(UUID reportId) {
        return mapper.selectByReportId(reportId).stream()
                .map(ReviewRiskItemResponse::from)
                .toList();
    }

    @Transactional
    public void replaceReportRisks(UUID reportId, UUID taskId, Object risks) {
        mapper.deleteByReportId(reportId);
        if (!(risks instanceof List<?> list)) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> risk)) {
                continue;
            }
            ReviewRiskItemEntity entity = new ReviewRiskItemEntity();
            entity.setId(UUID.randomUUID());
            entity.setReportId(reportId);
            entity.setTaskId(taskId);
            entity.setRiskType(text(firstPresent(risk, "type", "riskType")));
            entity.setRiskLevel(text(firstPresent(risk, "level", "riskLevel")));
            entity.setEvidence(text(risk.get("evidence")));
            entity.setEvidenceLocation(mapValue(risk.get("evidenceLocation")));
            entity.setSuggestion(text(risk.get("suggestion")));
            entity.setDetector(defaultText(risk.get("detector"), "MODEL"));
            entity.setConfidence(clampConfidence(risk.get("confidence")));
            entity.setStatus(defaultText(risk.get("status"), "OPEN").toUpperCase());
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            mapper.insert(entity);
        }
    }

    @Transactional
    public ReviewRiskItemResponse updateStatus(UUID riskId, String status, String reviewerNote) {
        ReviewRiskItemEntity entity = mapper.selectById(riskId);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "风险项不存在");
        }
        if (status != null && !status.isBlank()) {
            entity.setStatus(status.trim().toUpperCase());
        }
        entity.setReviewerNote(blankToNull(reviewerNote));
        entity.setUpdatedAt(OffsetDateTime.now());
        mapper.updateById(entity);
        return ReviewRiskItemResponse.from(entity);
    }

    private Object firstPresent(Map<?, ?> map, String primaryKey, String fallbackKey) {
        Object value = map.get(primaryKey);
        return value == null ? map.get(fallbackKey) : value;
    }

    private String defaultText(Object value, String fallback) {
        String text = text(value);
        return text == null || text.isBlank() ? fallback : text;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private BigDecimal clampConfidence(Object value) {
        BigDecimal parsed = parseDecimal(value);
        if (parsed == null) {
            return null;
        }
        if (parsed.compareTo(ZERO) < 0) {
            return ZERO;
        }
        if (parsed.compareTo(ONE) > 0) {
            return ONE;
        }
        return parsed;
    }

    private BigDecimal parseDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return new BigDecimal(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
