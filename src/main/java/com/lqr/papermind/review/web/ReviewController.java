package com.lqr.papermind.review.web;

import com.lqr.papermind.auth.security.RoleCodes;
import com.lqr.papermind.auth.security.SecurityUserPrincipal;
import com.lqr.papermind.common.constant.MetadataKeys;
import com.lqr.papermind.document.dto.DocumentUploadAcceptedResponse;
import com.lqr.papermind.document.dto.PageResponse;
import com.lqr.papermind.document.entity.DocumentIngestionJob;
import com.lqr.papermind.document.model.DocumentIngestionMessage;
import com.lqr.papermind.document.service.DocumentIngestionJobService;
import com.lqr.papermind.document.service.DocumentIngestionProducer;
import com.lqr.papermind.document.service.DocumentUploadStorageService;
import com.lqr.papermind.document.structured.dto.PaperStructuredParseResponse;
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
import com.lqr.papermind.review.service.ReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 评审任务控制器
 * <p>提供评审任务的上传、查询、共识、报告、风险和评审标准等API接口</p>
 */
@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final DocumentIngestionJobService documentIngestionJobService;
    private final DocumentUploadStorageService documentUploadStorageService;
    private final DocumentIngestionProducer documentIngestionProducer;

    /**
     * 上传评审论文
     *
     * @param principal 当前认证用户
     * @param file      上传的文件
     * @param sourceId  来源ID（可选）
     * @param title     论文标题（可选）
     * @return 上传结果，包含文档摄取任务信息
     * @throws IOException 文件读取异常
     */
    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentUploadAcceptedResponse> uploadReviewPaper(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                                            @RequestParam("file") MultipartFile file,
                                                                            @RequestParam(value = "sourceId", required = false) String sourceId,
                                                                            @RequestParam(value = "title", required = false) String title) throws IOException {
        requireReviewer(principal);
        UUID jobId = UUID.randomUUID();
        String resolvedSourceId = sourceId == null || sourceId.isBlank() ? UUID.randomUUID().toString() : sourceId.trim();
        String fileName = file.getOriginalFilename();
        DocumentUploadStorageService.StoredUpload upload = documentUploadStorageService.store(
                principal.getId(),
                resolvedSourceId,
                jobId,
                file,
                fileName
        );
        DocumentIngestionJob job = documentIngestionJobService.createJob(
                jobId,
                principal.getId(),
                resolvedSourceId,
                upload.fileName(),
                upload.filePath(),
                title,
                Map.of(MetadataKeys.SOURCE_TYPE, MetadataKeys.SOURCE_TYPE_REVIEW)
        );
        documentIngestionProducer.publish(new DocumentIngestionMessage(job.getId(), principal.getId(), resolvedSourceId));
        DocumentIngestionJob currentJob = documentIngestionJobService.findJob(principal.getId(), job.getId()).orElse(job);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(DocumentUploadAcceptedResponse.from(currentJob));
    }

    /**
     * 分页查询评审任务列表
     *
     * @param principal 当前认证用户
     * @param keyword   搜索关键词（可选）
     * @param status    任务状态筛选（可选）
     * @param page      页码（从0开始）
     * @param size      每页大小
     * @return 分页后的评审任务列表
     */
    @GetMapping("/tasks")
    public PageResponse<ReviewTaskResponse> listTasks(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                      @RequestParam(value = "keyword", required = false) String keyword,
                                                      @RequestParam(value = "status", required = false) String status,
                                                      @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
                                                      @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        requireReviewer(principal);
        return reviewService.listTasks(principal.getId(), isAdmin(principal), keyword, status, page, size);
    }

    /**
     * 创建评审任务
     *
     * @param principal 当前认证用户
     * @param request   评审任务创建请求
     * @return 创建的评审任务信息
     */
    @PostMapping("/tasks")
    public ReviewTaskResponse createTask(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                         @Valid @RequestBody ReviewTaskCreateRequest request) {
        return reviewService.createTask(principal.getId(), request);
    }

    /**
     * 获取评审任务详情
     *
     * @param principal 当前认证用户
     * @param taskId    评审任务ID
     * @return 评审任务详细信息
     */
    @GetMapping("/tasks/{taskId}")
    public ReviewTaskResponse getTask(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                      @PathVariable UUID taskId) {
        requireReviewer(principal);
        return reviewService.getTask(principal.getId(), isAdmin(principal), taskId);
    }

    /**
     * 获取评审任务共识
     *
     * @param principal 当前认证用户
     * @param taskId    评审任务ID
     * @return 评审共识信息
     */
    @GetMapping("/tasks/{taskId}/consensus")
    public ReviewConsensusResponse getConsensus(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                 @PathVariable UUID taskId) {
        requireReviewer(principal);
        return reviewService.getConsensus(principal.getId(), isAdmin(principal), taskId);
    }

    /**
     * 更新评审任务共识
     *
     * @param principal 当前认证用户
     * @param taskId    评审任务ID
     * @param request   共识更新请求
     * @return 更新后的评审共识信息
     */
    @PatchMapping("/tasks/{taskId}/consensus")
    public ReviewConsensusResponse updateConsensus(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                    @PathVariable UUID taskId,
                                                    @Valid @RequestBody ReviewConsensusUpdateRequest request) {
        requireReviewer(principal);
        return reviewService.updateConsensus(principal.getId(), isAdmin(principal), taskId, request);
    }

    /**
     * 确认评审任务共识
     *
     * @param principal 当前认证用户
     * @param taskId    评审任务ID
     * @return 确认后的评审共识信息
     */
    @PostMapping("/tasks/{taskId}/consensus/confirm")
    public ReviewConsensusResponse confirmConsensus(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                     @PathVariable UUID taskId) {
        requireReviewer(principal);
        return reviewService.confirmConsensus(principal.getId(), isAdmin(principal), taskId);
    }

    /**
     * 生成AI评审报告
     *
     * @param principal 当前认证用户
     * @param taskId    评审任务ID
     * @return AI生成的评审报告
     */
    @PostMapping("/tasks/{taskId}/ai-review")
    public ReviewReportResponse generateAiReview(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                 @PathVariable UUID taskId) {
        requireReviewer(principal);
        return reviewService.generateAiReview(principal.getId(), isAdmin(principal), taskId);
    }

    /**
     * 获取论文结构化解析结果
     *
     * @param principal 当前认证用户
     * @param taskId    评审任务ID
     * @return 结构化解析响应
     */
    @GetMapping("/tasks/{taskId}/structured-parse")
    public PaperStructuredParseResponse getStructuredParse(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                           @PathVariable UUID taskId) {
        requireReviewer(principal);
        return reviewService.getStructuredParse(principal.getId(), isAdmin(principal), taskId);
    }

    /**
     * 重新生成论文结构化解析结果
     *
     * @param principal 当前认证用户
     * @param taskId    评审任务ID
     * @return 重新生成的结构化解析响应
     */
    @PostMapping("/tasks/{taskId}/structured-parse/regenerate")
    public PaperStructuredParseResponse regenerateStructuredParse(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                                  @PathVariable UUID taskId) {
        requireReviewer(principal);
        return reviewService.regenerateStructuredParse(principal.getId(), isAdmin(principal), taskId);
    }

    /**
     * 更新评审报告
     *
     * @param principal 当前认证用户
     * @param reportId  评审报告ID
     * @param request   报告更新请求
     * @return 更新后的评审报告
     */
    @PatchMapping("/reports/{reportId}")
    public ReviewReportResponse updateReport(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                             @PathVariable UUID reportId,
                                             @Valid @RequestBody ReviewReportUpdateRequest request) {
        requireReviewer(principal);
        return reviewService.updateReport(principal.getId(), isAdmin(principal), reportId, request);
    }


    /**
     * 提交评审分配
     *
     * @param principal    当前认证用户
     * @param assignmentId 评审分配ID
     * @return 提交后的评审分配信息
     */
    @PostMapping("/assignments/{assignmentId}/submit")
    public ReviewAssignmentResponse submitAssignment(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                     @PathVariable UUID assignmentId) {
        requireReviewer(principal);
        return reviewService.submitAssignment(principal.getId(), assignmentId);
    }

    /**
     * 获取评审报告风险列表
     *
     * @param principal 当前认证用户
     * @param reportId  评审报告ID
     * @return 风险项列表
     */
    @GetMapping("/reports/{reportId}/risks")
    public List<ReviewRiskItemResponse> listRisks(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                  @PathVariable UUID reportId) {
        requireReviewer(principal);
        return reviewService.listRisks(principal.getId(), isAdmin(principal), reportId);
    }

    /**
     * 更新风险项
     *
     * @param principal 当前认证用户
     * @param riskId    风险项ID
     * @param request   风险更新请求
     * @return 更新后的风险项
     */
    @PutMapping("/risks/{riskId}")
    public ReviewRiskItemResponse updateRisk(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                             @PathVariable UUID riskId,
                                             @Valid @RequestBody ReviewRiskUpdateRequest request) {
        requireReviewer(principal);
        return reviewService.updateRisk(principal.getId(), isAdmin(principal), riskId, request);
    }

    /**
     * 确认风险项
     *
     * @param principal 当前认证用户
     * @param riskId    风险项ID
     * @param request   风险更新请求（可选）
     * @return 确认后的风险项
     */
    @PostMapping("/risks/{riskId}/confirm")
    public ReviewRiskItemResponse confirmRisk(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                              @PathVariable UUID riskId,
                                              @RequestBody(required = false) ReviewRiskUpdateRequest request) {
        requireReviewer(principal);
        return reviewService.updateRisk(principal.getId(), isAdmin(principal), riskId, statusRequest("CONFIRMED", request));
    }

    /**
     * 忽略风险项
     *
     * @param principal 当前认证用户
     * @param riskId    风险项ID
     * @param request   风险更新请求（可选）
     * @return 忽略后的风险项
     */
    @PostMapping("/risks/{riskId}/ignore")
    public ReviewRiskItemResponse ignoreRisk(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                             @PathVariable UUID riskId,
                                             @RequestBody(required = false) ReviewRiskUpdateRequest request) {
        requireReviewer(principal);
        return reviewService.updateRisk(principal.getId(), isAdmin(principal), riskId, statusRequest("IGNORED", request));
    }

    /**
     * 解决风险项
     *
     * @param principal 当前认证用户
     * @param riskId    风险项ID
     * @param request   风险更新请求（可选）
     * @return 解决后的风险项
     */
    @PostMapping("/risks/{riskId}/resolve")
    public ReviewRiskItemResponse resolveRisk(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                              @PathVariable UUID riskId,
                                              @RequestBody(required = false) ReviewRiskUpdateRequest request) {
        requireReviewer(principal);
        return reviewService.updateRisk(principal.getId(), isAdmin(principal), riskId, statusRequest("RESOLVED", request));
    }

    /**
     * 获取评审标准列表
     *
     * @param principal       当前认证用户
     * @param includeDisabled 是否包含已禁用的标准
     * @return 评审标准列表
     */
    @GetMapping("/criteria")
    public List<ReviewCriterionResponse> listCriteria(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                      @RequestParam(value = "includeDisabled", defaultValue = "false") boolean includeDisabled) {
        requireReviewer(principal);
        return reviewService.listCriteria(includeDisabled && isAdmin(principal));
    }

    /**
     * 创建评审标准
     *
     * @param principal 当前认证用户
     * @param request   评审标准创建请求
     * @return 创建的评审标准
     */
    @PostMapping("/criteria")
    public ReviewCriterionResponse createCriterion(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                   @Valid @RequestBody ReviewCriterionRequest request) {
        requireAdmin(principal);
        return reviewService.createCriterion(request);
    }

    /**
     * 更新评审标准
     *
     * @param principal 当前认证用户
     * @param id        评审标准ID
     * @param request   评审标准更新请求
     * @return 更新后的评审标准
     */
    @PatchMapping("/criteria/{id}")
    public ReviewCriterionResponse updateCriterion(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                   @PathVariable UUID id,
                                                   @Valid @RequestBody ReviewCriterionRequest request) {
        requireAdmin(principal);
        return reviewService.updateCriterion(id, request);
    }

    /**
     * 构建风险状态更新请求
     *
     * @param status  目标状态
     * @param request 原始请求（可选）
     * @return 包含状态的更新请求
     */
    private ReviewRiskUpdateRequest statusRequest(String status, ReviewRiskUpdateRequest request) {
        return new ReviewRiskUpdateRequest(status, request == null ? null : request.reviewerNote());
    }

    /**
     * 验证用户是否具有评审员权限
     *
     * @param principal 当前认证用户
     * @throws ResponseStatusException 权限不足时抛出403异常
     */
    private void requireReviewer(SecurityUserPrincipal principal) {
        if (principal == null || (!isAdmin(principal) && !principal.getRoles().contains(RoleCodes.REVIEWER))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要评审员权限");
        }
    }

    /**
     * 验证用户是否具有管理员权限
     *
     * @param principal 当前认证用户
     * @throws ResponseStatusException 权限不足时抛出403异常
     */
    private void requireAdmin(SecurityUserPrincipal principal) {
        if (!isAdmin(principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要管理员权限");
        }
    }

    /**
     * 判断用户是否为管理员
     *
     * @param principal 当前认证用户
     * @return 是否为管理员
     */
    private boolean isAdmin(SecurityUserPrincipal principal) {
        return principal.getRoles().contains(RoleCodes.ADMIN);
    }
}
