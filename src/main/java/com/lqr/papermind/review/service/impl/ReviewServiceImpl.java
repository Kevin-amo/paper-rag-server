package com.lqr.papermind.review.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.papermind.ai.service.LlmService;
import com.lqr.papermind.ai.service.PromptConstructionService;
import com.lqr.papermind.common.constant.MetadataKeys;
import com.lqr.papermind.document.dto.DocumentDetailResponse;
import com.lqr.papermind.document.dto.PageResponse;
import com.lqr.papermind.document.entity.DocumentEntity;
import com.lqr.papermind.document.mapper.DocumentMapper;
import com.lqr.papermind.document.service.DocumentPersistenceService;
import com.lqr.papermind.document.structured.dto.PaperStructuredParseResponse;
import com.lqr.papermind.document.structured.service.PaperStructuredParseService;
import com.lqr.papermind.review.dto.ReviewAssignmentResponse;
import com.lqr.papermind.review.dto.ReviewConsensusResponse;
import com.lqr.papermind.review.dto.ReviewConsensusUpdateRequest;
import com.lqr.papermind.review.dto.ReviewCriterionRequest;
import com.lqr.papermind.review.dto.ReviewCriterionResponse;
import com.lqr.papermind.review.dto.ReviewReportResponse;
import com.lqr.papermind.review.dto.ReviewReportUpdateRequest;
import com.lqr.papermind.review.dto.ReviewRiskItemResponse;
import com.lqr.papermind.review.dto.ReviewRiskUpdateRequest;
import com.lqr.papermind.review.dto.ReviewTaskCreateRequest;
import com.lqr.papermind.review.dto.ReviewTaskResponse;
import com.lqr.papermind.review.audit.ReviewAuditService;
import com.lqr.papermind.review.assessment.ReviewOutputParser;
import com.lqr.papermind.review.entity.ReviewAssignmentEntity;
import com.lqr.papermind.review.entity.ReviewAuditLogEntity;
import com.lqr.papermind.review.entity.ReviewCriterionEntity;
import com.lqr.papermind.review.entity.ReviewReportEntity;
import com.lqr.papermind.review.entity.ReviewRiskItemEntity;
import com.lqr.papermind.review.entity.ReviewTaskEntity;
import com.lqr.papermind.review.mapper.ReviewAssignmentMapper;
import com.lqr.papermind.review.mapper.ReviewAuditLogMapper;
import com.lqr.papermind.review.mapper.ReviewCriterionMapper;
import com.lqr.papermind.review.mapper.ReviewReportMapper;
import com.lqr.papermind.review.mapper.ReviewTaskMapper;
import com.lqr.papermind.review.model.ReviewAssignmentStatuses;
import com.lqr.papermind.review.model.ReviewTaskStatuses;
import com.lqr.papermind.review.risk.ReferenceFormatChecker;
import com.lqr.papermind.review.risk.ReviewRiskService;
import com.lqr.papermind.review.service.ReviewAssignmentService;
import com.lqr.papermind.review.service.ReviewConsensusService;
import com.lqr.papermind.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Array;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int PAPER_TEXT_LIMIT = 10000;
    private static final ZoneId REVIEW_ZONE = ZoneId.of("Asia/Shanghai");

    private final ReviewTaskMapper taskMapper;
    private final ReviewReportMapper reportMapper;
    private final ReviewAssignmentMapper assignmentMapper;
    private final ReviewCriterionMapper criterionMapper;
    private final ReviewAuditLogMapper auditLogMapper;
    private final ReviewAssignmentService assignmentService;
    private final DocumentMapper documentMapper;
    private final DocumentPersistenceService documentPersistenceService;
    private final PaperStructuredParseService paperStructuredParseService;
    private final LlmService llmService;
    private final ReviewConsensusService consensusService;
    private final ReviewOutputParser reviewOutputParser;
    private final ReferenceFormatChecker referenceFormatChecker;
    private final ReviewAuditService reviewAuditService;
    private final ReviewRiskService reviewRiskService;
    private final ObjectMapper objectMapper;

    /**
     * 分页查询评审任务列表
     *
     * @param currentUserId 当前用户ID
     * @param admin         是否为管理员
     * @param keyword       搜索关键词（按标题或来源ID模糊匹配）
     * @param status        任务状态过滤
     * @param page          页码（从0开始）
     * @param size          每页大小
     * @return 分页后的评审任务列表
     */
    @Override
    public PageResponse<ReviewTaskResponse> listTasks(UUID currentUserId, boolean admin, String keyword, String status, int page, int size) {
        taskMapper.syncFromDocuments();
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        if (!admin) {
            List<ReviewTaskEntity> reviewerTasks = taskMapper.selectReviewerTasks(
                    currentUserId,
                    blankToNull(keyword),
                    normalizeReviewerAssignmentStatus(status)
            );
            long offset = (long) safePage * safeSize;
            if (offset >= reviewerTasks.size()) {
                return new PageResponse<>(List.of(), safePage, safeSize, reviewerTasks.size());
            }
            int fromIndex = (int) offset;
            int toIndex = Math.min(fromIndex + safeSize, reviewerTasks.size());
            List<ReviewTaskResponse> items = reviewerTasks.subList(fromIndex, toIndex).stream()
                    .map(task -> toTaskResponse(task, false, currentUserId, false))
                    .toList();
            return new PageResponse<>(items, safePage, safeSize, reviewerTasks.size());
        }
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
                .map(task -> toTaskResponse(task, false, currentUserId, true))
                .toList();
        return new PageResponse<>(items, safePage, safeSize, result.getTotal());
    }

    /**
     * 获取单个评审任务详情
     *
     * @param currentUserId 当前用户ID
     * @param admin         是否为管理员
     * @param taskId        任务ID
     * @return 评审任务详情
     */
    @Override
    public ReviewTaskResponse getTask(UUID currentUserId, boolean admin, UUID taskId) {
        ReviewTaskEntity task = requireTask(taskId);
        return toTaskResponse(task, true, currentUserId, admin);
    }

    /**
     * 创建评审任务
     *
     * @param currentUserId 当前用户ID
     * @param request       创建请求参数
     * @return 创建的评审任务
     */
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
        task.setStatus(ReviewTaskStatuses.PENDING_ASSIGNMENT);
        OffsetDateTime now = OffsetDateTime.now();
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);
        appendAudit(task.getId(), currentUserId, "CREATE_TASK", "创建评审任务", Map.of("sourceId", sourceId));
        return toTaskResponse(task, true, currentUserId, true);
    }

    /**
     * 为已索引的评审文档自动创建任务
     *
     * @param ownerUserId 文档所有者用户ID
     * @param sourceId    文档来源标识
     */
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
        task.setStatus(ReviewTaskStatuses.PENDING_ASSIGNMENT);
        OffsetDateTime now = OffsetDateTime.now();
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);
        appendAudit(task.getId(), ownerUserId, "CREATE_TASK", "评审文档入库完成后自动创建任务", Map.of("sourceId", sourceId));
    }

    /**
     * 生成AI辅助评审报告
     *
     * @param currentUserId 当前用户ID
     * @param admin         是否为管理员
     * @param taskId        任务ID
     * @return 生成的评审报告
     */
    @Override
    @Transactional
    public ReviewReportResponse generateAiReview(UUID currentUserId, boolean admin, UUID taskId) {
        ReviewTaskEntity task = requireTask(taskId);
        DocumentPersistenceService.DocumentDetail document = requireDocument(task);
        List<ReviewCriterionResponse> criteria = listCriteria(false);
        if (criteria.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先配置评审标准");
        }

        ReviewAssignmentEntity assignment = assignmentMapper.selectActiveByTaskAndReviewer(taskId, currentUserId);
        boolean hasAssignments = false;
        if (assignment == null) {
            hasAssignments = !assignmentMapper.selectByTaskId(taskId).isEmpty();
        }
        if (assignment == null && hasAssignments) {
            if (admin) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "管理员不能覆盖已分配评审人的个人报告");
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能访问分配给自己的评审任务");
        }
        if (assignment == null && !admin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能访问分配给自己的评审任务");
        }
        if (assignment != null && ReviewAssignmentStatuses.SUBMITTED.equals(assignment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "评审已提交，不能重新生成报告");
        }

        if (assignment != null) {
            if (!ReviewAssignmentStatuses.REVIEWING.equals(assignment.getStatus())) {
                assignmentMapper.updateStatus(assignment.getId(), ReviewAssignmentStatuses.REVIEWING);
            }
            taskMapper.updateTaskStatus(task.getId(), ReviewTaskStatuses.IN_REVIEW);
        } else {
            taskMapper.updateTaskStatus(task.getId(), ReviewTaskStatuses.IN_REVIEW);
        }
        String modelText = llmService.generate(buildReviewPrompt(document, criteria));
        Map<String, Object> parsed;
        try {
            parsed = reviewOutputParser.parse(modelText);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        }
        List<ReferenceFormatChecker.ReferenceRisk> referenceRisks = referenceFormatChecker.check(referenceCheckerInput(document, parsed));
        ReviewReportEntity report = assignment == null
                ? reportMapper.selectLatestByTaskId(task.getId())
                : reportMapper.selectByAssignmentId(assignment.getId());
        boolean creating = report == null;
        OffsetDateTime now = OffsetDateTime.now();
        if (creating) {
            report = new ReviewReportEntity();
            report.setId(UUID.randomUUID());
            report.setTaskId(task.getId());
            report.setDocumentId(task.getDocumentId());
            if (assignment != null) {
                report.setAssignmentId(assignment.getId());
            }
            report.setCreatedAt(now);
        }
        report.setReviewerUserId(currentUserId);
        report.setPaperSections(mapValue(parsed.get("paperSections")));
        report.setScores(valueOrDefault(parsed.get("scores"), List.of()));
        report.setComments(mapValue(parsed.get("comments")));
        report.setRisks(mergeRisks(valueOrDefault(parsed.get("risks"), List.of()), referenceRisks));
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
        reviewRiskService.replaceReportRisks(report.getId(), task.getId(), report.getRisks());
        appendAudit(task.getId(), currentUserId, "AI_REVIEW", "生成 AI 辅助评审报告", Map.of("reportId", report.getId().toString()));
        return ReviewReportResponse.from(assignment == null
                ? reportMapper.selectLatestByTaskId(task.getId())
                : reportMapper.selectByAssignmentId(assignment.getId()));
    }

    /**
     * 获取论文结构化解析结果
     *
     * @param currentUserId 当前用户ID
     * @param admin         是否为管理员
     * @param taskId        任务ID
     * @return 结构化解析结果
     */
    @Override
    public PaperStructuredParseResponse getStructuredParse(UUID currentUserId, boolean admin, UUID taskId) {
        ReviewTaskEntity task = requireAccessibleTask(currentUserId, admin, taskId);
        requireDocument(task);
        return paperStructuredParseService.find(task.getSubmitterUserId(), task.getSourceId())
                .map(PaperStructuredParseResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "结构化解析结果不存在"));
    }

    /**
     * 重新生成论文结构化解析结果
     *
     * @param currentUserId 当前用户ID
     * @param admin         是否为管理员
     * @param taskId        任务ID
     * @return 重新生成的结构化解析结果
     */
    @Override
    @Transactional
    public PaperStructuredParseResponse regenerateStructuredParse(UUID currentUserId, boolean admin, UUID taskId) {
        ReviewTaskEntity task = requireAccessibleTask(currentUserId, admin, taskId);
        requireDocument(task);
        return PaperStructuredParseResponse.from(
                paperStructuredParseService.regenerate(task.getSubmitterUserId(), task.getSourceId())
        );
    }

    /**
     * 更新评审报告
     *
     * @param currentUserId 当前用户ID
     * @param admin         是否为管理员
     * @param reportId      报告ID
     * @param request       更新请求参数
     * @return 更新后的评审报告
     */
    @Override
    @Transactional
    public ReviewReportResponse updateReport(UUID currentUserId, boolean admin, UUID reportId, ReviewReportUpdateRequest request) {
        ReviewReportEntity report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "评审报告不存在");
        }
        ReviewTaskEntity task = requireTask(report.getTaskId());
        Map<String, Object> beforeSnapshot = reportSnapshot(report);
        boolean risksProvided = request.risks() != null;
        ReviewAssignmentEntity assignment = null;
        if (report.getAssignmentId() != null) {
            assignment = assignmentMapper.selectById(report.getAssignmentId());
            if (assignment == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "评审分配不存在");
            }
            if (!admin && !assignment.getReviewerUserId().equals(currentUserId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能修改自己的评审报告");
            }
            if (ReviewAssignmentStatuses.CANCELLED.equals(assignment.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "评审分配已取消，不能修改报告");
            }
            if (!admin) {
                ReviewAssignmentEntity activeAssignment = assignmentMapper.selectActiveByTaskAndReviewer(task.getId(), currentUserId);
                if (activeAssignment == null || !activeAssignment.getId().equals(assignment.getId())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能修改自己的评审报告");
                }
            }
            if (ReviewAssignmentStatuses.SUBMITTED.equals(assignment.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "评审已提交，不能修改报告");
            }
            if (ReviewAssignmentStatuses.ASSIGNED.equals(assignment.getStatus())) {
                assignmentMapper.updateStatus(assignment.getId(), ReviewAssignmentStatuses.REVIEWING);
            }
        } else if (!admin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能修改自己的评审报告");
        }
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
        Map<String, Object> afterSnapshot = reportSnapshot(report);
        report.setManualDelta(manualDelta(beforeSnapshot, afterSnapshot));
        reportMapper.updateById(report);
        if (assignment != null) {
            taskMapper.updateTaskStatus(task.getId(), ReviewTaskStatuses.IN_REVIEW);
        } else if ("CONFIRMED".equals(nextStatus) || "COMPLETED".equals(nextStatus)) {
            taskMapper.updateTaskStatus(task.getId(), ReviewTaskStatuses.SUBMITTED);
        } else {
            taskMapper.updateTaskStatus(task.getId(), ReviewTaskStatuses.IN_REVIEW);
        }
        if (risksProvided) {
            reviewRiskService.replaceReportRisks(report.getId(), task.getId(), report.getRisks());
        }
        reviewAuditService.append(task.getId(), currentUserId, "ADJUST_REPORT", "人工调整评审报告", beforeSnapshot, afterSnapshot, Map.of());
        return ReviewReportResponse.from(reportMapper.selectById(reportId));
    }

    /**
     * 获取评审报告的风险项列表
     *
     * @param currentUserId 当前用户ID
     * @param admin         是否为管理员
     * @param reportId      报告ID
     * @return 风险项列表
     */
    @Override
    public List<ReviewRiskItemResponse> listRisks(UUID currentUserId, boolean admin, UUID reportId) {
        ReviewReportEntity report = requireReport(reportId);
        assertReportAccess(currentUserId, admin, report);
        return reviewRiskService.listByReportId(reportId);
    }

    /**
     * 更新风险项状态和评审备注
     *
     * @param currentUserId 当前用户ID
     * @param admin         是否为管理员
     * @param riskId        风险项ID
     * @param request       更新请求参数，包含状态和评审备注
     * @return 更新后的风险项
     */
    @Override
    public ReviewRiskItemResponse updateRisk(UUID currentUserId, boolean admin, UUID riskId, ReviewRiskUpdateRequest request) {
        ReviewRiskItemEntity risk = reviewRiskService.findById(riskId);
        if (risk == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "风险项不存在");
        }
        ReviewReportEntity report = requireReport(risk.getReportId());
        assertReportAccess(currentUserId, admin, report);
        return reviewRiskService.updateStatus(riskId, request.status(), request.reviewerNote());
    }


    /**
     * 提交评审分配
     *
     * @param currentUserId 当前用户ID
     * @param assignmentId  评审分配ID
     * @return 提交后的评审分配
     */
    @Override
    @Transactional
    public ReviewAssignmentResponse submitAssignment(UUID currentUserId, UUID assignmentId) {
        ReviewAssignmentEntity assignment = assignmentMapper.selectById(assignmentId);
        if (assignment == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "评审分配不存在");
        }
        if (!assignment.getReviewerUserId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能提交自己的评审任务");
        }
        if (ReviewAssignmentStatuses.CANCELLED.equals(assignment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "评审分配已取消，不能提交");
        }
        ReviewReportEntity report = reportMapper.selectByAssignmentId(assignmentId);
        if (report == null && !ReviewAssignmentStatuses.SUBMITTED.equals(assignment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先生成或保存评审报告再提交");
        }
        return assignmentService.submitAssignment(currentUserId, assignmentId);
    }

    /**
     * 获取评审共识汇总
     *
     * @param currentUserId 当前用户ID
     * @param admin         是否为管理员
     * @param taskId        任务ID
     * @return 评审共识汇总
     */
    @Override
    public ReviewConsensusResponse getConsensus(UUID currentUserId, boolean admin, UUID taskId) {
        if (!consensusService.canAccessConsensus(currentUserId, admin, taskId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有组长或管理员可以查看共识汇总");
        }
        return consensusService.getForTask(taskId);
    }

    /**
     * 更新评审共识汇总
     *
     * @param currentUserId 当前用户ID
     * @param admin         是否为管理员
     * @param taskId        任务ID
     * @param request       更新请求参数
     * @return 更新后的评审共识汇总
     */
    @Override
    public ReviewConsensusResponse updateConsensus(UUID currentUserId, boolean admin, UUID taskId, ReviewConsensusUpdateRequest request) {
        if (!consensusService.canAccessConsensus(currentUserId, admin, taskId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有组长或管理员可以修改共识汇总");
        }
        return consensusService.update(taskId, currentUserId, request);
    }

    /**
     * 确认评审共识汇总
     *
     * @param currentUserId 当前用户ID
     * @param admin         是否为管理员
     * @param taskId        任务ID
     * @return 确认后的评审共识汇总
     */
    @Override
    public ReviewConsensusResponse confirmConsensus(UUID currentUserId, boolean admin, UUID taskId) {
        if (!consensusService.canAccessConsensus(currentUserId, admin, taskId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有组长或管理员可以确认共识汇总");
        }
        return consensusService.confirm(taskId, currentUserId);
    }

    /**
     * 获取评审指标列表
     *
     * @param includeDisabled 是否包含已禁用的指标
     * @return 评审指标列表
     */
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

    /**
     * 创建评审指标
     *
     * @param request 评审指标创建请求
     * @return 创建的评审指标
     */
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

    /**
     * 更新评审指标
     *
     * @param id      评审指标ID
     * @param request 评审指标更新请求
     * @return 更新后的评审指标
     */
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

    /**
     * 将评审任务实体转换为响应对象
     *
     * @param task          评审任务实体
     * @param includeDocument 是否包含文档详情
     * @param currentUserId 当前用户ID
     * @param admin         是否为管理员
     * @return 评审任务响应对象
     */
    private ReviewTaskResponse toTaskResponse(ReviewTaskEntity task, boolean includeDocument, UUID currentUserId, boolean admin) {
        if (!admin) {
            ReviewAssignmentEntity currentAssignment = assignmentMapper.selectActiveByTaskAndReviewer(task.getId(), currentUserId);
            if (currentAssignment == null) {
                if (!isOwnUnassignedPendingTask(task, currentUserId)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能访问分配给自己的评审任务");
                }
                DocumentDetailResponse document = includeDocument ? DocumentDetailResponse.from(requireDocument(task)) : null;
                return ReviewTaskResponse.from(task, document, null, null, List.of());
            }
            DocumentDetailResponse document = includeDocument ? DocumentDetailResponse.from(requireDocument(task)) : null;
            ReviewReportResponse report = ReviewReportResponse.from(reportMapper.selectByAssignmentId(currentAssignment.getId()));
            return ReviewTaskResponse.from(
                    task,
                    document,
                    report,
                    ReviewAssignmentResponse.from(currentAssignment),
                    List.of(ReviewAssignmentResponse.from(currentAssignment))
            );
        }
        DocumentDetailResponse document = includeDocument ? DocumentDetailResponse.from(requireDocument(task)) : null;
        List<ReviewAssignmentResponse> assignments = assignmentMapper.selectByTaskId(task.getId()).stream()
                .map(ReviewAssignmentResponse::from)
                .toList();
        ReviewReportResponse report = ReviewReportResponse.from(reportMapper.selectLatestByTaskId(task.getId()));
        return ReviewTaskResponse.from(task, document, report, null, assignments);
    }

    /**
     * 根据报告ID获取评审报告，不存在则抛出异常
     *
     * @param reportId 报告ID
     * @return 评审报告实体
     */
    private ReviewReportEntity requireReport(UUID reportId) {
        ReviewReportEntity report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "评审报告不存在");
        }
        return report;
    }

    /**
     * 断言当前用户有权访问指定评审报告
     *
     * @param currentUserId 当前用户ID
     * @param admin         是否为管理员
     * @param report        评审报告实体
     */
    private void assertReportAccess(UUID currentUserId, boolean admin, ReviewReportEntity report) {
        if (admin) {
            return;
        }
        if (report.getAssignmentId() != null) {
            ReviewAssignmentEntity assignment = assignmentMapper.selectById(report.getAssignmentId());
            ReviewAssignmentEntity activeAssignment = assignment == null
                    ? null
                    : assignmentMapper.selectActiveByTaskAndReviewer(assignment.getTaskId(), currentUserId);
            if (assignment != null
                    && !ReviewAssignmentStatuses.CANCELLED.equals(assignment.getStatus())
                    && currentUserId.equals(assignment.getReviewerUserId())
                    && activeAssignment != null
                    && activeAssignment.getId().equals(assignment.getId())) {
                return;
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能访问自己的评审风险项");
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能访问自己的评审风险项");
    }

    /**
     * 构建AI评审提示词
     *
     * @param document 文档详情
     * @param criteria 评审标准列表
     * @return AI提示词对象
     */
    private PromptConstructionService.Prompt buildReviewPrompt(DocumentPersistenceService.DocumentDetail document, List<ReviewCriterionResponse> criteria) {
        String criteriaText = criteria.stream()
                .map(item -> "- " + item.code() + " / " + item.name() + "：满分 " + item.maxScore() + "，权重 " + item.weight() + "。" + nullToEmpty(item.description()))
                .toList()
                .stream()
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        String paperText = truncate(document.contentText(), PAPER_TEXT_LIMIT);
        String structuredText = structuredParseText(document);
        String reviewDate = LocalDate.now(REVIEW_ZONE).toString();
        String systemMessage = "你是论文辅助评审平台的评审助手。只输出一个严格 JSON 对象；第一个字符必须是 {，最后一个字符必须是 }。禁止 Markdown、代码围栏、解释文字、前后缀。";
        String userMessage = "请对以下论文进行辅助评审。\n"
                + "当前评审日期:" + reviewDate + "（时区:Asia/Shanghai）。\n"
                + "参考文献中的访问日期/引用日期若不晚于当前评审日期，不得判定为未来日期；YYYY-MM-DD 格式是合法日期格式。\n"
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

    /**
     * 解析模型输出的JSON文本
     *
     * @param modelText 模型输出的原始文本
     * @return 解析后的键值对映射
     */
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

    /**
     * 从模型输出中提取JSON字符串
     *
     * @param value 模型原始输出
     * @return 提取的JSON字符串
     */
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

    /**
     * 去除文本中的Markdown代码围栏
     *
     * @param value 包含代码围栏的文本
     * @return 去除围栏后的文本
     */
    private String stripCodeFence(String value) {
        String text = value.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        return text;
    }

    /**
     * 通过括号匹配找到JSON对象的结束位置
     *
     * @param text  待解析的文本
     * @param start 左花括号的起始位置
     * @return 匹配的右花括号位置，未找到返回-1
     */
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

    /**
     * 修复JSON中常见的尾逗号问题
     *
     * @param json 待修复的JSON字符串
     * @return 修复后的JSON字符串
     */
    private String repairJson(String json) {
        return json.replaceAll(",\\s*([}\\]])", "$1");
    }

    /**
     * 根据任务ID获取评审任务，不存在则抛出异常
     *
     * @param taskId 任务ID
     * @return 评审任务实体
     */
    private ReviewTaskEntity requireTask(UUID taskId) {
        ReviewTaskEntity task = taskMapper.selectByIdIncludingDeleted(taskId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "评审任务不存在");
        }
        return task;
    }

    /**
     * 获取当前用户可访问的评审任务，无权限则抛出异常
     *
     * @param currentUserId 当前用户ID
     * @param admin         是否为管理员
     * @param taskId        任务ID
     * @return 评审任务实体
     */
    private ReviewTaskEntity requireAccessibleTask(UUID currentUserId, boolean admin, UUID taskId) {
        ReviewTaskEntity task = requireTask(taskId);
        if (!admin
                && assignmentMapper.selectActiveByTaskAndReviewer(taskId, currentUserId) == null
                && !isOwnUnassignedPendingTask(task, currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能访问分配给自己的评审任务");
        }
        return task;
    }

    /**
     * 判断是否为用户自己创建的、未分配评审人的待处理任务
     *
     * @param task          评审任务实体
     * @param currentUserId 当前用户ID
     * @return 是否为自己的未分配待处理任务
     */
    private boolean isOwnUnassignedPendingTask(ReviewTaskEntity task, UUID currentUserId) {
        return task != null
                && currentUserId != null
                && currentUserId.equals(task.getSubmitterUserId())
                && ReviewTaskStatuses.PENDING_ASSIGNMENT.equals(task.getStatus())
                && assignmentMapper.selectByTaskId(task.getId()).stream()
                .noneMatch(assignment -> !ReviewAssignmentStatuses.CANCELLED.equals(assignment.getStatus()));
    }

    /**
     * 获取评审任务关联的文档详情，不存在则抛出异常
     *
     * @param task 评审任务实体
     * @return 文档详情
     */
    private DocumentPersistenceService.DocumentDetail requireDocument(ReviewTaskEntity task) {
        return documentPersistenceService.findReviewDocument(task.getSubmitterUserId(), task.getSourceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "论文文档不存在"));
    }

    /**
     * 根据用户ID和来源ID查找文档实体
     *
     * @param ownerUserId 文档所有者用户ID
     * @param sourceId    文档来源标识
     * @return 文档实体
     */
    private DocumentEntity findDocumentEntity(UUID ownerUserId, String sourceId) {
        DocumentEntity entity = documentMapper.selectOne(new LambdaQueryWrapper<DocumentEntity>()
                .eq(DocumentEntity::getOwnerUserId, ownerUserId)
                .eq(DocumentEntity::getSourceId, sourceId));
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文档不存在");
        }
        return entity;
    }

    /**
     * 根据用户ID和来源ID查找评审文档实体
     *
     * @param ownerUserId 文档所有者用户ID
     * @param sourceId    文档来源标识
     * @return 评审文档实体
     */
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

    /**
     * 将评审指标请求应用到评审指标实体
     *
     * @param entity  评审指标实体
     * @param request 评审指标请求
     */
    private void applyCriterionRequest(ReviewCriterionEntity entity, ReviewCriterionRequest request) {
        entity.setCode(requireText(request.code(), "指标编码不能为空").toUpperCase());
        entity.setName(requireText(request.name(), "指标名称不能为空"));
        entity.setDescription(blankToNull(request.description()));
        entity.setMaxScore(request.maxScore() == null ? 100 : request.maxScore());
        entity.setWeight(request.weight() == null ? 20 : request.weight());
        entity.setEnabled(request.enabled() == null || request.enabled());
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
    }

    /**
     * 追加审计日志
     *
     * @param taskId         任务ID
     * @param operatorUserId 操作用户ID
     * @param action         操作类型
     * @param note           操作备注
     * @param snapshot       操作快照
     */
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

    /**
     * 生成评审报告快照
     *
     * @param report 评审报告实体
     * @return 报告快照映射
     */
    private Map<String, Object> reportSnapshot(ReviewReportEntity report) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("reportId", report.getId() == null ? null : report.getId().toString());
        snapshot.put("paperSections", report.getPaperSections());
        snapshot.put("scores", report.getScores());
        snapshot.put("comments", report.getComments());
        snapshot.put("risks", report.getRisks());
        snapshot.put("totalScore", report.getTotalScore());
        snapshot.put("finalRecommendation", report.getFinalRecommendation());
        snapshot.put("status", report.getStatus());
        return snapshot;
    }

    /**
     * 计算人工调整的差异
     *
     * @param before 调整前快照
     * @param after  调整后快照
     * @return 差异映射
     */
    private Map<String, Object> manualDelta(Map<String, Object> before, Map<String, Object> after) {
        return Map.of(
                "scoreChanged", !Objects.equals(before.get("scores"), after.get("scores")) || !Objects.equals(before.get("totalScore"), after.get("totalScore")),
                "commentEdited", !Objects.equals(before.get("comments"), after.get("comments")),
                "riskOverridden", !Objects.equals(before.get("risks"), after.get("risks")),
                "finalRecommendationChanged", !Objects.equals(before.get("finalRecommendation"), after.get("finalRecommendation"))
        );
    }

    /**
     * 合并解析结果和原始模型输出
     *
     * @param parsed    解析后的结果
     * @param modelText 模型原始输出
     * @return 合并后的输出
     */
    private Map<String, Object> rawOutput(Map<String, Object> parsed, String modelText) {
        Map<String, Object> raw = new LinkedHashMap<>(parsed);
        raw.put("rawText", modelText);
        return raw;
    }

    /**
     * 获取文档的结构化解析文本
     *
     * @param document 文档详情
     * @return 结构化解析的JSON文本
     */
    private String structuredParseText(DocumentPersistenceService.DocumentDetail document) {
        return paperStructuredParseService.find(document.ownerUserId(), document.sourceId())
                .map(result -> toJsonText(result.getMergedResult()))
                .orElse("{}");
    }

    /**
     * 将对象值转换为Map
     *
     * @param value 待转换的值
     * @return 转换后的Map，如果无法转换则返回空Map
     */
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return new LinkedHashMap<>();
    }

    /**
     * 获取值或返回默认值
     *
     * @param value    原始值
     * @param fallback 默认值
     * @return 原始值或默认值
     */
    private Object valueOrDefault(Object value, Object fallback) {
        return value == null ? fallback : value;
    }

    /**
     * 从解析结果中提取参考文献文本
     *
     * @param parsed 解析后的结果
     * @return 参考文献文本
     */
    private String structuredReferences(Map<String, Object> parsed) {
        Object sections = parsed.get("paperSections");
        if (sections instanceof Map<?, ?> map) {
            return referencesText(map.get("references"));
        }
        return "";
    }

    /**
     * 获取参考文献检查器的输入文本
     *
     * @param document 文档详情
     * @param parsed   解析后的结果
     * @return 参考文献文本
     */
    private String referenceCheckerInput(DocumentPersistenceService.DocumentDetail document, Map<String, Object> parsed) {
        String structuredParseReferences = paperStructuredParseService.find(document.ownerUserId(), document.sourceId())
                .map(result -> referencesFromStructuredParse(result.getMergedResult()))
                .orElse("");
        return structuredParseReferences.isBlank() ? structuredReferences(parsed) : structuredParseReferences;
    }

    /**
     * 从结构化解析结果中提取参考文献
     *
     * @param mergedResult 合并后的结构化解析结果
     * @return 参考文献文本
     */
    private String referencesFromStructuredParse(Object mergedResult) {
        if (mergedResult instanceof Map<?, ?> map) {
            String directReferences = referencesText(map.get("references"));
            if (!directReferences.isBlank()) {
                return directReferences;
            }
            Object sections = map.get("paperSections");
            if (sections instanceof Map<?, ?> sectionMap) {
                return referencesText(sectionMap.get("references"));
            }
        }
        return "";
    }

    /**
     * 将参考文献对象转换为文本
     *
     * @param references 参考文献对象（可以是List、数组或单个对象）
     * @return 参考文献文本
     */
    private String referencesText(Object references) {
        if (references == null) {
            return "";
        }
        if (references instanceof List<?> list) {
            return String.join(System.lineSeparator(), list.stream().map(String::valueOf).toList());
        }
        if (references.getClass().isArray()) {
            List<String> entries = new ArrayList<>();
            for (int index = 0; index < Array.getLength(references); index++) {
                entries.add(String.valueOf(Array.get(references, index)));
            }
            return String.join(System.lineSeparator(), entries);
        }
        return String.valueOf(references);
    }

    /**
     * 合并模型风险项和参考文献格式检查风险项
     *
     * @param modelRisks      模型生成的风险项
     * @param referenceRisks  参考文献格式检查风险项
     * @return 合并后的风险项列表
     */
    private Object mergeRisks(Object modelRisks, List<ReferenceFormatChecker.ReferenceRisk> referenceRisks) {
        List<Object> merged = new ArrayList<>();
        if (modelRisks instanceof List<?> list) {
            merged.addAll(list);
        }
        for (ReferenceFormatChecker.ReferenceRisk risk : referenceRisks) {
            merged.add(Map.of(
                    "type", risk.riskType(),
                    "level", risk.riskLevel(),
                    "evidence", risk.evidence(),
                    "suggestion", risk.suggestion(),
                    "detector", "REFERENCE_RULE",
                    "confidence", risk.confidence()
            ));
        }
        return merged;
    }

    /**
     * 计算总分（取各指标分数的平均值）
     *
     * @param scores 各指标分数列表
     * @return 计算后的总分
     */
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

    /**
     * 将对象值转换为Integer，无法转换则返回null
     *
     * @param value 待转换的值
     * @return 转换后的Integer，无法转换返回null
     */
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

    /**
     * 将对象值转换为int，无法转换则返回默认值
     *
     * @param value    待转换的值
     * @param fallback 默认值
     * @return 转换后的int值
     */
    private int intValue(Object value, int fallback) {
        Integer parsed = intOrNull(value);
        return parsed == null ? fallback : parsed;
    }

    /**
     * 将对象值转换为String，无法转换则返回默认值
     *
     * @param value    待转换的值
     * @param fallback 默认值
     * @return 转换后的字符串
     */
    private String stringValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    /**
     * 要求文本非空，为空则抛出异常
     *
     * @param value   待验证的值
     * @param message 错误信息
     * @return 验证后的文本
     */
    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    /**
     * 将空字符串或null转换为null
     *
     * @param value 待处理的字符串
     * @return 处理后的字符串，空或null返回null
     */
    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 将评审人任务状态标准化为评审分配状态
     *
     * @param status 评审人任务状态
     * @return 标准化后的评审分配状态
     */
    private String normalizeReviewerAssignmentStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase();
        return switch (normalized) {
            case ReviewTaskStatuses.ASSIGNED -> ReviewAssignmentStatuses.ASSIGNED;
            case ReviewTaskStatuses.IN_REVIEW -> ReviewAssignmentStatuses.REVIEWING;
            case ReviewTaskStatuses.SUBMITTED -> ReviewAssignmentStatuses.SUBMITTED;
            case ReviewAssignmentStatuses.RETURNED -> ReviewAssignmentStatuses.RETURNED;
            default -> normalized;
        };
    }

    /**
     * 将null值转换为空字符串
     *
     * @param value 待处理的值
     * @return 处理后的字符串，null返回空字符串
     */
    private String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 返回第一个非空的字符串
     *
     * @param values 候选字符串数组
     * @return 第一个非空的字符串，都为空则返回空字符串
     */
    private String nonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    /**
     * 截断字符串到指定长度
     *
     * @param value 待截断的字符串
     * @param limit 最大长度
     * @return 截断后的字符串
     */
    private String truncate(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return nullToEmpty(value);
        }
        return value.substring(0, limit) + "\n[后续内容因长度限制已截断]";
    }

    /**
     * 将对象转换为JSON文本
     *
     * @param value 待转换的对象
     * @return JSON文本，转换失败返回字符串表示
     */
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
