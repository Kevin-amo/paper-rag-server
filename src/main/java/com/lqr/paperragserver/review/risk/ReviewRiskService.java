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
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewRiskService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final Set<String> ALLOWED_RISK_LEVELS = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> ALLOWED_STATUSES = Set.of("OPEN", "CONFIRMED", "IGNORED", "RESOLVED");

    private final ReviewRiskItemMapper mapper;

    public ReviewRiskItemEntity findById(UUID riskId) {
        return mapper.selectById(riskId);
    }

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
            entity.setRiskType(normalizeRiskType(firstPresent(risk, "type", "riskType")));
            entity.setRiskLevel(normalizeRiskLevel(firstPresent(risk, "level", "riskLevel")));
            entity.setEvidence(text(risk.get("evidence")));
            entity.setEvidenceLocation(mapValue(risk.get("evidenceLocation")));
            entity.setSuggestion(text(risk.get("suggestion")));
            entity.setDetector(defaultText(risk.get("detector"), "MODEL"));
            entity.setConfidence(clampConfidence(risk.get("confidence")));
            entity.setStatus("OPEN");
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
            entity.setStatus(normalizeStatus(status));
        }
        entity.setReviewerNote(blankToNull(reviewerNote));
        entity.setUpdatedAt(OffsetDateTime.now());
        mapper.updateById(entity);
        return ReviewRiskItemResponse.from(entity);
    }

    private String normalizeRiskType(Object value) {
        String text = text(value);
        return text == null || text.isBlank() ? "UNKNOWN" : text;
    }

    private String normalizeRiskLevel(Object value) {
        String text = text(value);
        if (text == null || text.isBlank()) {
            return "LOW";
        }
        String normalized = text.toUpperCase();
        return ALLOWED_RISK_LEVELS.contains(normalized) ? normalized : "LOW";
    }

    private String normalizeStatus(String value) {
        String normalized = value.trim().toUpperCase();
        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "\u98ce\u9669\u9879\u72b6\u6001\u975e\u6cd5");
        }
        return normalized;
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
