package com.lqr.papermind.review.risk;

import com.lqr.papermind.review.dto.ReviewRiskItemResponse;
import com.lqr.papermind.review.entity.ReviewRiskItemEntity;
import com.lqr.papermind.review.mapper.ReviewRiskItemMapper;
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

    /**
     * 根据风险项ID查询单个风险项。
     *
     * @param riskId 风险项唯一标识
     * @return 匹配的风险项实体，不存在时返回 null
     */
    public ReviewRiskItemEntity findById(UUID riskId) {
        return mapper.selectById(riskId);
    }

    /**
     * 根据评审报告ID查询所有关联的风险项，并转换为响应DTO。
     *
     * @param reportId 评审报告唯一标识
     * @return 该报告下所有风险项的响应列表
     */
    public List<ReviewRiskItemResponse> listByReportId(UUID reportId) {
        return mapper.selectByReportId(reportId).stream()
                .map(ReviewRiskItemResponse::from)
                .toList();
    }

    /**
     * 替换指定评审报告下的全部风险项。
     * 先删除旧数据，再根据传入的风险列表逐条插入新记录。
     *
     * @param reportId 评审报告唯一标识
     * @param taskId   关联任务唯一标识
     * @param risks    风险项列表（List&lt;Map&gt; 格式），非列表类型时静默跳过
     */
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

    /**
     * 更新风险项的状态及评审备注。
     *
     * @param riskId       风险项唯一标识
     * @param status       新状态（OPEN / CONFIRMED / IGNORED / RESOLVED），为空时保持原状态
     * @param reviewerNote 评审人备注，为空白时视为 null
     * @return 更新后的风险项响应DTO
     * @throws ResponseStatusException 风险项不存在时返回 404，状态值非法时返回 400
     */
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

    /**
     * 标准化风险类型名称。空值或空白值默认为 "UNKNOWN"。
     *
     * @param value 原始风险类型值
     * @return 标准化后的风险类型字符串
     */
    private String normalizeRiskType(Object value) {
        String text = text(value);
        return text == null || text.isBlank() ? "UNKNOWN" : text;
    }

    /**
     * 标准化风险等级。空值默认为 "LOW"，不在允许集合中的值也回退为 "LOW"。
     *
     * @param value 原始风险等级值
     * @return 标准化后的风险等级字符串
     */
    private String normalizeRiskLevel(Object value) {
        String text = text(value);
        if (text == null || text.isBlank()) {
            return "LOW";
        }
        String normalized = text.toUpperCase();
        return ALLOWED_RISK_LEVELS.contains(normalized) ? normalized : "LOW";
    }

    /**
     * 标准化风险项状态值。
     *
     * @param value 原始状态字符串
     * @return 大写后的合法状态值
     * @throws ResponseStatusException 当状态值不在允许集合中时抛出 400
     */
    private String normalizeStatus(String value) {
        String normalized = value.trim().toUpperCase();
        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "风险项状态非法");
        }
        return normalized;
    }

    /**
     * 从 Map 中按优先级获取值：优先使用 primaryKey，若为 null 则回退到 fallbackKey。
     *
     * @param map         源数据映射
     * @param primaryKey  首选键名
     * @param fallbackKey 备选键名
     * @return 首次出现的非 null 值，均不存在时返回 null
     */
    private Object firstPresent(Map<?, ?> map, String primaryKey, String fallbackKey) {
        Object value = map.get(primaryKey);
        return value == null ? map.get(fallbackKey) : value;
    }

    /**
     * 获取文本值，为空或空白时返回默认值。
     *
     * @param value    原始值
     * @param fallback 默认文本
     * @return 处理后的字符串
     */
    private String defaultText(Object value, String fallback) {
        String text = text(value);
        return text == null || text.isBlank() ? fallback : text;
    }

    /**
     * 将任意对象转为去除首尾空白的字符串，null 值返回 null。
     *
     * @param value 原始对象
     * @return 去除空白后的字符串，或 null
     */
    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    /**
     * 将空白或 null 字符串转换为 null，非空白时保留去除首尾空白的结果。
     *
     * @param value 原始字符串
     * @return 处理后的字符串或 null
     */
    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 将任意值安全转换为 String→Object 的 Map。非 Map 类型返回空 Map。
     *
     * @param value 原始值
     * @return 保持插入顺序的 LinkedHashMap
     */
    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    /**
     * 将置信度值限制在 [0, 1] 范围内。无法解析时返回 null。
     *
     * @param value 原始置信度值
     * @return 钳位后的 BigDecimal，或 null
     */
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

    /**
     * 尝试将任意值解析为 BigDecimal，支持 BigDecimal、Number 和字符串类型。
     *
     * @param value 原始值
     * @return 解析后的 BigDecimal，无法解析时返回 null
     */
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
