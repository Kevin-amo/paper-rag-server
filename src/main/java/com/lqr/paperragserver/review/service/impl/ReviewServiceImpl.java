package com.lqr.paperragserver.review.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.paperragserver.ai.service.LlmService;
import com.lqr.paperragserver.ai.service.PromptConstructionService;
import com.lqr.paperragserver.common.constant.MetadataKeys;
import com.lqr.paperragserver.document.dto.DocumentDetailResponse;
import com.lqr.paperragserver.document.dto.PageResponse;
import com.lqr.paperragserver.document.entity.DocumentEntity;
import com.lqr.paperragserver.document.mapper.DocumentMapper;
import com.lqr.paperragserver.document.service.DocumentPersistenceService;
import com.lqr.paperragserver.document.structured.service.PaperStructuredParseService;
import com.lqr.paperragserver.review.dto.ReviewCriterionRequest;
import com.lqr.paperragserver.review.dto.ReviewCriterionResponse;
import com.lqr.paperragserver.review.dto.ReviewReportResponse;
import com.lqr.paperragserver.review.dto.ReviewReportUpdateRequest;
import com.lqr.paperragserver.review.dto.ReviewTaskCreateRequest;
import com.lqr.paperragserver.review.dto.ReviewTaskResponse;
import com.lqr.paperragserver.review.entity.ReviewAuditLogEntity;
import com.lqr.paperragserver.review.entity.ReviewCriterionEntity;
import com.lqr.paperragserver.review.entity.ReviewReportEntity;
import com.lqr.paperragserver.review.entity.ReviewTaskEntity;
import com.lqr.paperragserver.review.mapper.ReviewAuditLogMapper;
import com.lqr.paperragserver.review.mapper.ReviewCriterionMapper;
import com.lqr.paperragserver.review.mapper.ReviewReportMapper;
import com.lqr.paperragserver.review.mapper.ReviewTaskMapper;
import com.lqr.paperragserver.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int PAPER_TEXT_LIMIT = 10000;

    private final ReviewTaskMapper taskMapper;
    private final ReviewReportMapper reportMapper;
    private final ReviewCriterionMapper criterionMapper;
    private final ReviewAuditLogMapper auditLogMapper;
    private final DocumentMapper documentMapper;
    private final DocumentPersistenceService documentPersistenceService;
    private final PaperStructuredParseService paperStructuredParseService;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    @Override
    public PageResponse<ReviewTaskResponse> listTasks(UUID currentUserId, boolean admin, String keyword, String status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        LambdaQueryWrapper<ReviewTaskEntity> wrapper = new LambdaQueryWrapper<ReviewTaskEntity>()
                .orderByDesc(ReviewTaskEntity::getUpdatedAt)
                .orderByDesc(ReviewTaskEntity::getCreatedAt);
        if (keyword != null && !keyword.isBlank()) {
            String like = keyword.trim();
            wrapper.and(item -> item.like(ReviewTaskEntity::getTitle, like)
                    .or()
                    .like(ReviewTaskEntity::getSourceId, like));
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(ReviewTaskEntity::getStatus, status.trim().toUpperCase());
        }
        Page<ReviewTaskEntity> result = taskMapper.selectPage(new Page<>(safePage + 1L, safeSize), wrapper);
        List<ReviewTaskResponse> items = result.getRecords().stream()
                .map(task -> toTaskResponse(task, false))
                .toList();
        return new PageResponse<>(items, safePage, safeSize, result.getTotal());
    }

    @Override
    public ReviewTaskResponse getTask(UUID currentUserId, boolean admin, UUID taskId) {
        ReviewTaskEntity task = requireTask(taskId);
        return toTaskResponse(task, true);
    }

    @Override
    @Transactional
    public ReviewTaskResponse createTask(UUID currentUserId, ReviewTaskCreateRequest request) {
        String sourceId = requireText(request.sourceId(), "文档标识不能为空");
        DocumentPersistenceService.DocumentDetail document = documentPersistenceService.findReviewDocument(currentUserId, sourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "评审文档不存在"));
        ReviewTaskEntity task = new ReviewTaskEntity();
        task.setId(UUID.randomUUID());
        task.setDocumentId(findReviewDocumentEntity(currentUserId, sourceId).getId());
        task.setSubmitterUserId(currentUserId);
        task.setSourceId(sourceId);
        task.setTitle(nonBlank(request.title(), document.title(), sourceId));
        task.setStatus("PENDING");
        OffsetDateTime now = OffsetDateTime.now();
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);
        appendAudit(task.getId(), currentUserId, "CREATE_TASK", "创建评审任务", Map.of("sourceId", sourceId));
        return toTaskResponse(task, true);
    }

    @Override
    @Transactional
    public void createTaskForIndexedReviewDocument(UUID ownerUserId, String sourceId) {
        if (ownerUserId == null || sourceId == null || sourceId.isBlank()) {
            return;
        }
        DocumentPersistenceService.DocumentDetail document = documentPersistenceService.findReviewDocument(ownerUserId, sourceId)
                .orElse(null);
        if (document == null || !"INDEXED".equalsIgnoreCase(document.status())) {
            return;
        }
        DocumentEntity entity = findReviewDocumentEntity(ownerUserId, sourceId);
        if (taskMapper.existsByDocumentId(entity.getId())) {
            return;
        }
        ReviewTaskEntity task = new ReviewTaskEntity();
        task.setId(UUID.randomUUID());
        task.setDocumentId(entity.getId());
        task.setSubmitterUserId(ownerUserId);
        task.setSourceId(sourceId);
        task.setTitle(nonBlank(document.title(), sourceId));
        task.setStatus("PENDING");
        OffsetDateTime now = OffsetDateTime.now();
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);
        appendAudit(task.getId(), ownerUserId, "CREATE_TASK", "评审文档入库完成后自动创建任务", Map.of("sourceId", sourceId));
    }

    @Override
    @Transactional
    public ReviewReportResponse generateAiReview(UUID currentUserId, boolean admin, UUID taskId) {
        ReviewTaskEntity task = requireTask(taskId);
        DocumentPersistenceService.DocumentDetail document = requireDocument(task);
        List<ReviewCriterionResponse> criteria = listCriteria(false);
        if (criteria.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先配置评审标准");
        }

        taskMapper.updateStatus(task.getId(), currentUserId, "REVIEWING");
        String modelText = llmService.generate(buildReviewPrompt(document, criteria));
        Map<String, Object> parsed = parseModelOutput(modelText);
        ReviewReportEntity report = reportMapper.selectLatestByTaskId(task.getId());
        boolean creating = report == null;
        OffsetDateTime now = OffsetDateTime.now();
        if (creating) {
            report = new ReviewReportEntity();
            report.setId(UUID.randomUUID());
            report.setTaskId(task.getId());
            report.setDocumentId(task.getDocumentId());
            report.setCreatedAt(now);
        }
        report.setReviewerUserId(currentUserId);
        report.setPaperSections(mapValue(parsed.get("paperSections")));
        report.setScores(valueOrDefault(parsed.get("scores"), List.of()));
        report.setComments(mapValue(parsed.get("comments")));
        report.setRisks(valueOrDefault(parsed.get("risks"), List.of()));
        report.setRawModelOutput(rawOutput(parsed, modelText));
        report.setTotalScore(intValue(parsed.get("totalScore"), calculateTotalScore(parsed.get("scores"))));
        report.setFinalRecommendation(stringValue(parsed.get("finalRecommendation"), "建议人工复核后进入下一评审环节"));
        report.setStatus("AI_GENERATED");
        report.setGeneratedAt(now);
        report.setUpdatedAt(now);
        if (creating) {
            reportMapper.insert(report);
        } else {
            reportMapper.updateById(report);
        }
        appendAudit(task.getId(), currentUserId, "AI_REVIEW", "生成 AI 辅助评审报告", Map.of("reportId", report.getId().toString()));
        return ReviewReportResponse.from(reportMapper.selectLatestByTaskId(task.getId()));
    }

    @Override
    @Transactional
    public ReviewReportResponse updateReport(UUID currentUserId, boolean admin, UUID reportId, ReviewReportUpdateRequest request) {
        ReviewReportEntity report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "评审报告不存在");
        }
        ReviewTaskEntity task = requireTask(report.getTaskId());
        report.setReviewerUserId(currentUserId);
        if (request.paperSections() != null) {
            report.setPaperSections(request.paperSections());
        }
        if (request.scores() != null) {
            report.setScores(request.scores());
        }
        if (request.comments() != null) {
            report.setComments(request.comments());
        }
        if (request.risks() != null) {
            report.setRisks(request.risks());
        }
        if (request.totalScore() != null) {
            report.setTotalScore(request.totalScore());
        }
        if (request.finalRecommendation() != null) {
            report.setFinalRecommendation(blankToNull(request.finalRecommendation()));
        }
        String nextStatus = request.status() == null || request.status().isBlank() ? "ADJUSTED" : request.status().trim().toUpperCase();
        report.setStatus(nextStatus);
        report.setAdjustedAt(OffsetDateTime.now());
        report.setUpdatedAt(OffsetDateTime.now());
        reportMapper.updateById(report);
        if ("CONFIRMED".equals(nextStatus) || "COMPLETED".equals(nextStatus)) {
            taskMapper.updateStatus(task.getId(), currentUserId, "COMPLETED");
        } else {
            taskMapper.updateStatus(task.getId(), currentUserId, "REVIEWING");
        }
        appendAudit(task.getId(), currentUserId, "ADJUST_REPORT", "人工调整评审报告", Map.of("reportId", reportId.toString(), "status", nextStatus));
        return ReviewReportResponse.from(reportMapper.selectById(reportId));
    }

    @Override
    public List<ReviewCriterionResponse> listCriteria(boolean includeDisabled) {
        LambdaQueryWrapper<ReviewCriterionEntity> wrapper = new LambdaQueryWrapper<ReviewCriterionEntity>()
                .orderByAsc(ReviewCriterionEntity::getSortOrder)
                .orderByAsc(ReviewCriterionEntity::getCreatedAt);
        if (!includeDisabled) {
            wrapper.eq(ReviewCriterionEntity::getEnabled, true);
        }
        return criterionMapper.selectList(wrapper).stream()
                .map(ReviewCriterionResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public ReviewCriterionResponse createCriterion(ReviewCriterionRequest request) {
        ReviewCriterionEntity entity = new ReviewCriterionEntity();
        entity.setId(UUID.randomUUID());
        applyCriterionRequest(entity, request);
        OffsetDateTime now = OffsetDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        criterionMapper.insert(entity);
        return ReviewCriterionResponse.from(entity);
    }

    @Override
    @Transactional
    public ReviewCriterionResponse updateCriterion(UUID id, ReviewCriterionRequest request) {
        ReviewCriterionEntity entity = criterionMapper.selectById(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "评审指标不存在");
        }
        applyCriterionRequest(entity, request);
        entity.setUpdatedAt(OffsetDateTime.now());
        criterionMapper.updateById(entity);
        return ReviewCriterionResponse.from(criterionMapper.selectById(id));
    }

    private ReviewTaskResponse toTaskResponse(ReviewTaskEntity task, boolean includeDocument) {
        DocumentDetailResponse document = includeDocument ? DocumentDetailResponse.from(requireDocument(task)) : null;
        ReviewReportResponse report = ReviewReportResponse.from(reportMapper.selectLatestByTaskId(task.getId()));
        return ReviewTaskResponse.from(task, document, report);
    }

    private PromptConstructionService.Prompt buildReviewPrompt(DocumentPersistenceService.DocumentDetail document, List<ReviewCriterionResponse> criteria) {
        String criteriaText = criteria.stream()
                .map(item -> "- " + item.code() + " / " + item.name() + "：满分 " + item.maxScore() + "，权重 " + item.weight() + "。" + nullToEmpty(item.description()))
                .toList()
                .stream()
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        String paperText = truncate(document.contentText(), PAPER_TEXT_LIMIT);
        String structuredText = structuredParseText(document);
        String systemMessage = "你是论文辅助评审平台的评审助手。只输出一个严格 JSON 对象；第一个字符必须是 {，最后一个字符必须是 }。禁止 Markdown、代码围栏、解释文字、前后缀。";
        String userMessage = "请对以下论文进行辅助评审。\n"
                + "论文标题：" + nullToEmpty(document.title()) + "\n"
                + "摘要：" + nullToEmpty(document.abstractText()) + "\n"
                + "关键词：" + toJsonText(document.keywords()) + "\n\n"
                + "独立结构化解析结果：\n" + structuredText + "\n\n"
                + "评审标准：\n" + criteriaText + "\n\n"
                + "论文全文片段：\n" + paperText + "\n\n"
                + "输出要求：\n"
                + "1. 只能输出 JSON 对象，不能输出 ```json 或任何说明。\n"
                + "2. JSON 中不能出现尾逗号，字符串必须正确转义。\n"
                + "3. scores 必须覆盖每个评审标准；score 为 0 到 maxScore 的整数。\n"
                + "4. risks 可以为空数组；没有证据不要编造。\n"
                + "5. finalRecommendation 只能取：建议通过、建议修改后通过、建议复核、不建议通过。\n\n"
                + "JSON 模板：\n"
                + "{\n"
                + "  \"paperSections\": {\"title\": \"\", \"abstract\": \"\", \"introduction\": \"\", \"method\": \"\", \"conclusion\": \"\", \"keywords\": [], \"researchObject\": \"\", \"methodPath\": \"\"},\n"
                + "  \"scores\": [{\"code\": \"\", \"name\": \"\", \"score\": 0, \"maxScore\": 100, \"reason\": \"\", \"confidence\": 0.8}],\n"
                + "  \"comments\": {\"summary\": \"\", \"strengths\": [], \"weaknesses\": [], \"suggestions\": [], \"finalAdvice\": \"\"},\n"
                + "  \"risks\": [{\"type\": \"\", \"level\": \"LOW\", \"evidence\": \"\", \"suggestion\": \"\"}],\n"
                + "  \"totalScore\": 0,\n"
                + "  \"finalRecommendation\": \"建议复核\"\n"
                + "}\n"
                + "评审维度必须覆盖政策导向、专业匹配、创新性、逻辑性、语言质量；风险项必须检查政治不当表述、参考文献不规范、结构缺失和语言问题。";
        return new PromptConstructionService.Prompt(systemMessage, userMessage);
    }

    private Map<String, Object> parseModelOutput(String modelText) {
        String json = extractJson(modelText);
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException ex) {
            String repairedJson = repairJson(json);
            try {
                return objectMapper.readValue(repairedJson, new TypeReference<Map<String, Object>>() {
                });
            } catch (JsonProcessingException ignored) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "模型评审结果不是有效 JSON，请重试");
            }
        }
    }

    private String extractJson(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "模型评审结果为空");
        }
        String text = stripCodeFence(value.trim());
        int start = text.indexOf('{');
        if (start < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "模型评审结果缺少 JSON 对象");
        }
        int end = balancedObjectEnd(text, start);
        if (end < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "模型评审结果 JSON 对象不完整");
        }
        return text.substring(start, end + 1);
    }

    private String stripCodeFence(String value) {
        String text = value.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        return text;
    }

    private int balancedObjectEnd(String text, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int index = start; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    private String repairJson(String json) {
        return json.replaceAll(",\\s*([}\\]])", "$1");
    }

    private ReviewTaskEntity requireTask(UUID taskId) {
        ReviewTaskEntity task = taskMapper.selectByIdIncludingDeleted(taskId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "评审任务不存在");
        }
        return task;
    }

    private DocumentPersistenceService.DocumentDetail requireDocument(ReviewTaskEntity task) {
        return documentPersistenceService.findReviewDocument(task.getSubmitterUserId(), task.getSourceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "论文文档不存在"));
    }

    private DocumentEntity findDocumentEntity(UUID ownerUserId, String sourceId) {
        DocumentEntity entity = documentMapper.selectOne(new LambdaQueryWrapper<DocumentEntity>()
                .eq(DocumentEntity::getOwnerUserId, ownerUserId)
                .eq(DocumentEntity::getSourceId, sourceId));
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文档不存在");
        }
        return entity;
    }

    private DocumentEntity findReviewDocumentEntity(UUID ownerUserId, String sourceId) {
        DocumentEntity entity = documentMapper.selectOne(new LambdaQueryWrapper<DocumentEntity>()
                .eq(DocumentEntity::getOwnerUserId, ownerUserId)
                .eq(DocumentEntity::getSourceId, sourceId)
                .apply("metadata ->> 'sourceType' = {0}", MetadataKeys.SOURCE_TYPE_REVIEW));
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "评审文档不存在");
        }
        return entity;
    }

    private void applyCriterionRequest(ReviewCriterionEntity entity, ReviewCriterionRequest request) {
        entity.setCode(requireText(request.code(), "指标编码不能为空").toUpperCase());
        entity.setName(requireText(request.name(), "指标名称不能为空"));
        entity.setDescription(blankToNull(request.description()));
        entity.setMaxScore(request.maxScore() == null ? 100 : request.maxScore());
        entity.setWeight(request.weight() == null ? 20 : request.weight());
        entity.setEnabled(request.enabled() == null || request.enabled());
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
    }

    private void appendAudit(UUID taskId, UUID operatorUserId, String action, String note, Map<String, Object> snapshot) {
        ReviewAuditLogEntity log = new ReviewAuditLogEntity();
        log.setId(UUID.randomUUID());
        log.setTaskId(taskId);
        log.setOperatorUserId(operatorUserId);
        log.setAction(action);
        log.setNote(note);
        log.setSnapshot(snapshot);
        log.setCreatedAt(OffsetDateTime.now());
        auditLogMapper.insert(log);
    }

    private Map<String, Object> rawOutput(Map<String, Object> parsed, String modelText) {
        Map<String, Object> raw = new LinkedHashMap<>(parsed);
        raw.put("rawText", modelText);
        return raw;
    }

    private String structuredParseText(DocumentPersistenceService.DocumentDetail document) {
        return paperStructuredParseService.find(document.ownerUserId(), document.sourceId())
                .map(result -> toJsonText(result.getMergedResult()))
                .orElse("{}");
    }

    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return new LinkedHashMap<>();
    }

    private Object valueOrDefault(Object value, Object fallback) {
        return value == null ? fallback : value;
    }

    private int calculateTotalScore(Object scores) {
        if (!(scores instanceof List<?> list) || list.isEmpty()) {
            return 0;
        }
        List<Integer> values = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Integer score = intOrNull(map.get("score"));
                if (score != null) {
                    values.add(score);
                }
            }
        }
        if (values.isEmpty()) {
            return 0;
        }
        return (int) Math.round(values.stream().mapToInt(Integer::intValue).average().orElse(0));
    }

    private Integer intOrNull(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private int intValue(Object value, int fallback) {
        Integer parsed = intOrNull(value);
        return parsed == null ? fallback : parsed;
    }

    private String stringValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String nonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String truncate(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return nullToEmpty(value);
        }
        return value.substring(0, limit) + "\n[后续内容因长度限制已截断]";
    }

    private String toJsonText(Object value) {
        if (value == null) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }
}