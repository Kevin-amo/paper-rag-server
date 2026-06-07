package com.lqr.paperragserver.review.web;

import com.lqr.paperragserver.auth.security.RoleCodes;
import com.lqr.paperragserver.auth.security.SecurityUserPrincipal;
import com.lqr.paperragserver.common.constant.MetadataKeys;
import com.lqr.paperragserver.document.dto.DocumentUploadAcceptedResponse;
import com.lqr.paperragserver.document.dto.PageResponse;
import com.lqr.paperragserver.document.entity.DocumentIngestionJob;
import com.lqr.paperragserver.document.model.DocumentIngestionMessage;
import com.lqr.paperragserver.document.service.DocumentIngestionJobService;
import com.lqr.paperragserver.document.service.DocumentIngestionProducer;
import com.lqr.paperragserver.document.service.DocumentUploadStorageService;
import com.lqr.paperragserver.review.dto.ReviewCriterionRequest;
import com.lqr.paperragserver.review.dto.ReviewCriterionResponse;
import com.lqr.paperragserver.review.dto.ReviewReportResponse;
import com.lqr.paperragserver.review.dto.ReviewReportUpdateRequest;
import com.lqr.paperragserver.review.dto.ReviewRiskItemResponse;
import com.lqr.paperragserver.review.dto.ReviewRiskUpdateRequest;
import com.lqr.paperragserver.review.dto.ReviewTaskCreateRequest;
import com.lqr.paperragserver.review.dto.ReviewTaskResponse;
import com.lqr.paperragserver.review.service.ReviewService;
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

@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final DocumentIngestionJobService documentIngestionJobService;
    private final DocumentUploadStorageService documentUploadStorageService;
    private final DocumentIngestionProducer documentIngestionProducer;

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

    @GetMapping("/tasks")
    public PageResponse<ReviewTaskResponse> listTasks(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                      @RequestParam(value = "keyword", required = false) String keyword,
                                                      @RequestParam(value = "status", required = false) String status,
                                                      @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
                                                      @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        requireReviewer(principal);
        return reviewService.listTasks(principal.getId(), isAdmin(principal), keyword, status, page, size);
    }

    @PostMapping("/tasks")
    public ReviewTaskResponse createTask(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                         @Valid @RequestBody ReviewTaskCreateRequest request) {
        return reviewService.createTask(principal.getId(), request);
    }

    @GetMapping("/tasks/{taskId}")
    public ReviewTaskResponse getTask(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                      @PathVariable UUID taskId) {
        requireReviewer(principal);
        return reviewService.getTask(principal.getId(), isAdmin(principal), taskId);
    }

    @PostMapping("/tasks/{taskId}/ai-review")
    public ReviewReportResponse generateAiReview(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                 @PathVariable UUID taskId) {
        requireReviewer(principal);
        return reviewService.generateAiReview(principal.getId(), isAdmin(principal), taskId);
    }

    @PatchMapping("/reports/{reportId}")
    public ReviewReportResponse updateReport(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                             @PathVariable UUID reportId,
                                             @Valid @RequestBody ReviewReportUpdateRequest request) {
        requireReviewer(principal);
        return reviewService.updateReport(principal.getId(), isAdmin(principal), reportId, request);
    }

    @GetMapping("/reports/{reportId}/risks")
    public List<ReviewRiskItemResponse> listRisks(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                  @PathVariable UUID reportId) {
        requireReviewer(principal);
        return reviewService.listRisks(principal.getId(), isAdmin(principal), reportId);
    }

    @PutMapping("/risks/{riskId}")
    public ReviewRiskItemResponse updateRisk(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                             @PathVariable UUID riskId,
                                             @Valid @RequestBody ReviewRiskUpdateRequest request) {
        requireReviewer(principal);
        return reviewService.updateRisk(principal.getId(), isAdmin(principal), riskId, request);
    }

    @PostMapping("/risks/{riskId}/confirm")
    public ReviewRiskItemResponse confirmRisk(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                              @PathVariable UUID riskId,
                                              @RequestBody(required = false) ReviewRiskUpdateRequest request) {
        requireReviewer(principal);
        return reviewService.updateRisk(principal.getId(), isAdmin(principal), riskId, statusRequest("CONFIRMED", request));
    }

    @PostMapping("/risks/{riskId}/ignore")
    public ReviewRiskItemResponse ignoreRisk(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                             @PathVariable UUID riskId,
                                             @RequestBody(required = false) ReviewRiskUpdateRequest request) {
        requireReviewer(principal);
        return reviewService.updateRisk(principal.getId(), isAdmin(principal), riskId, statusRequest("IGNORED", request));
    }

    @PostMapping("/risks/{riskId}/resolve")
    public ReviewRiskItemResponse resolveRisk(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                              @PathVariable UUID riskId,
                                              @RequestBody(required = false) ReviewRiskUpdateRequest request) {
        requireReviewer(principal);
        return reviewService.updateRisk(principal.getId(), isAdmin(principal), riskId, statusRequest("RESOLVED", request));
    }

    @GetMapping("/criteria")
    public List<ReviewCriterionResponse> listCriteria(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                      @RequestParam(value = "includeDisabled", defaultValue = "false") boolean includeDisabled) {
        requireReviewer(principal);
        return reviewService.listCriteria(includeDisabled && isAdmin(principal));
    }

    @PostMapping("/criteria")
    public ReviewCriterionResponse createCriterion(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                   @Valid @RequestBody ReviewCriterionRequest request) {
        requireAdmin(principal);
        return reviewService.createCriterion(request);
    }

    @PatchMapping("/criteria/{id}")
    public ReviewCriterionResponse updateCriterion(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                   @PathVariable UUID id,
                                                   @Valid @RequestBody ReviewCriterionRequest request) {
        requireAdmin(principal);
        return reviewService.updateCriterion(id, request);
    }

    private ReviewRiskUpdateRequest statusRequest(String status, ReviewRiskUpdateRequest request) {
        return new ReviewRiskUpdateRequest(status, request == null ? null : request.reviewerNote());
    }

    private void requireReviewer(SecurityUserPrincipal principal) {
        if (!isAdmin(principal) && !principal.getRoles().contains(RoleCodes.REVIEWER)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要评审员权限");
        }
    }

    private void requireAdmin(SecurityUserPrincipal principal) {
        if (!isAdmin(principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要管理员权限");
        }
    }

    private boolean isAdmin(SecurityUserPrincipal principal) {
        return principal.getRoles().contains(RoleCodes.ADMIN);
    }
}