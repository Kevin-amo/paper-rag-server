package com.lqr.papermind.review.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.papermind.ai.service.LlmService;
import com.lqr.papermind.ai.service.PromptConstructionService;
import com.lqr.papermind.document.service.DocumentPersistenceService;
import com.lqr.papermind.document.structured.dto.PaperStructuredParseResponse;
import com.lqr.papermind.document.structured.entity.PaperStructuredParseEntity;
import com.lqr.papermind.document.structured.service.PaperStructuredParseService;
import com.lqr.papermind.review.assessment.ReviewOutputParser;
import com.lqr.papermind.review.audit.ReviewAuditService;
import com.lqr.papermind.review.dto.ReviewCriterionResponse;
import com.lqr.papermind.review.dto.ReviewReportUpdateRequest;
import com.lqr.papermind.review.dto.ReviewRiskUpdateRequest;
import com.lqr.papermind.review.entity.ReviewAssignmentEntity;
import com.lqr.papermind.review.entity.ReviewCriterionEntity;
import com.lqr.papermind.review.entity.ReviewReportEntity;
import com.lqr.papermind.review.entity.ReviewRiskItemEntity;
import com.lqr.papermind.review.entity.ReviewTaskEntity;
import com.lqr.papermind.review.mapper.ReviewAuditLogMapper;
import com.lqr.papermind.review.mapper.ReviewAssignmentMapper;
import com.lqr.papermind.review.mapper.ReviewCriterionMapper;
import com.lqr.papermind.review.mapper.ReviewReportMapper;
import com.lqr.papermind.review.mapper.ReviewTaskMapper;
import com.lqr.papermind.review.model.ReviewAssignmentStatuses;
import com.lqr.papermind.review.model.ReviewTaskStatuses;
import com.lqr.papermind.review.risk.ReferenceFormatChecker;
import com.lqr.papermind.review.risk.ReviewRiskService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewServiceImplTest {

    @Test
    void mergeRisksShouldAppendReferenceRisksWithoutDroppingModelRisks() throws Exception {
        List<Map<String, Object>> modelRisks = List.of(Map.of(
                "type", "STRUCTURE_MISSING",
                "level", "LOW",
                "evidence", "缺少讨论章节",
                "suggestion", "补充讨论"
        ));
        List<ReferenceFormatChecker.ReferenceRisk> referenceRisks = List.of(
                new ReferenceFormatChecker.ReferenceRisk("REFERENCE_FORMAT", "MEDIUM", "[1] 缺少年份", "补全年份", 0.82)
        );
        ReviewServiceImpl service = serviceWithRiskAccess(null, null, null);

        Method method = ReviewServiceImpl.class.getDeclaredMethod("mergeRisks", Object.class, List.class);
        method.setAccessible(true);
        Object merged = method.invoke(service, modelRisks, referenceRisks);

        assertThat((List<?>) merged).hasSize(2);
        assertThat((List<?>) merged).first().isEqualTo(modelRisks.get(0));
        assertThat((List<?>) merged).element(1).satisfies(item -> {
            Map<?, ?> referenceRisk = (Map<?, ?>) item;
            assertThat(referenceRisk.get("type")).isEqualTo("REFERENCE_FORMAT");
            assertThat(referenceRisk.get("level")).isEqualTo("MEDIUM");
            assertThat(referenceRisk.get("evidence")).isEqualTo("[1] 缺少年份");
            assertThat(referenceRisk.get("suggestion")).isEqualTo("补全年份");
            assertThat(referenceRisk.get("detector")).isEqualTo("REFERENCE_RULE");
            assertThat(referenceRisk.get("confidence")).isEqualTo(0.82);
        });
    }

    @Test
    void structuredReferencesShouldJoinListAndArrayEntriesWithNewlines() throws Exception {
        ReviewServiceImpl service = serviceWithRiskAccess(null, null, null);
        Method method = ReviewServiceImpl.class.getDeclaredMethod("structuredReferences", Map.class);
        method.setAccessible(true);

        Object fromList = method.invoke(service, Map.of(
                "paperSections", Map.of("references", List.of("[1] Missing year", "[2] Smith. 2024. Title"))
        ));
        Object fromArray = method.invoke(service, Map.of(
                "paperSections", Map.of("references", new String[]{"[1] Missing year", "[2] Smith. 2024. Title"})
        ));

        String expected = "[1] Missing year" + System.lineSeparator() + "[2] Smith. 2024. Title";
        assertThat(fromList).isEqualTo(expected);
        assertThat(fromArray).isEqualTo(expected);
        assertThat((String) fromList).doesNotStartWith("[[").doesNotContain(", ");
    }

    @Test
    void constructorShouldAcceptReviewOutputParserDependency() {
        ObjectMapper objectMapper = new ObjectMapper();
        ReviewServiceImpl service = new ReviewServiceImpl(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new ReviewOutputParser(objectMapper),
                new ReferenceFormatChecker(),
                mock(ReviewAuditService.class),
                mock(ReviewRiskService.class),
                objectMapper
        );

        assertThat(service).isNotNull();
    }

    @Test
    void buildReviewPromptShouldIncludeShanghaiReviewDateAndReferenceDateRule() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        PaperStructuredParseService paperStructuredParseService = mock(PaperStructuredParseService.class);
        when(paperStructuredParseService.find(ownerUserId, "source-1")).thenReturn(Optional.empty());
        ReviewServiceImpl service = serviceWithDependencies(
                null,
                null,
                null,
                null,
                null,
                null,
                paperStructuredParseService,
                null,
                null,
                new ReferenceFormatChecker(),
                mock(ReviewRiskService.class),
                mock(ReviewAuditService.class)
        );

        PromptConstructionService.Prompt prompt = buildReviewPrompt(
                service,
                document(ownerUserId, "source-1"),
                List.of(new ReviewCriterionResponse(
                        UUID.randomUUID(),
                        "REFERENCE",
                        "参考文献规范",
                        "检查参考文献日期",
                        100,
                        10,
                        1,
                        "REFERENCE",
                        true,
                        Map.of(),
                        true,
                        1,
                        OffsetDateTime.now(),
                        OffsetDateTime.now()
                ))
        );

        String today = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        assertThat(prompt.userMessage())
                .contains("当前评审日期:" + today)
                .contains("Asia/Shanghai")
                .contains("不晚于当前评审日期")
                .contains("不得判定为未来日期")
                .contains("YYYY-MM-DD");
    }

    @Test
    void generateAiReviewShouldWrapParserErrorsAsBadGateway() {
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        ReviewTaskMapper taskMapper = mock(ReviewTaskMapper.class);
        ReviewReportMapper reportMapper = mock(ReviewReportMapper.class);
        ReviewAssignmentMapper assignmentMapper = mock(ReviewAssignmentMapper.class);
        ReviewCriterionMapper criterionMapper = mock(ReviewCriterionMapper.class);
        ReviewAuditLogMapper auditLogMapper = mock(ReviewAuditLogMapper.class);
        DocumentPersistenceService documentPersistenceService = mock(DocumentPersistenceService.class);
        PaperStructuredParseService paperStructuredParseService = mock(PaperStructuredParseService.class);
        LlmService llmService = mock(LlmService.class);
        ReviewOutputParser reviewOutputParser = mock(ReviewOutputParser.class);
        ObjectMapper objectMapper = new ObjectMapper();

        ReviewTaskEntity task = new ReviewTaskEntity();
        task.setId(taskId);
        task.setDocumentId(documentId);
        task.setSubmitterUserId(userId);
        task.setSourceId("source-1");
        task.setTitle("title");
        task.setStatus("PENDING");
        when(taskMapper.selectByIdIncludingDeleted(taskId)).thenReturn(task);

        DocumentPersistenceService.DocumentDetail document = new DocumentPersistenceService.DocumentDetail(
                "source-1",
                userId,
                "title",
                null,
                null,
                null,
                null,
                List.of(),
                "abstract",
                null,
                null,
                2024,
                List.of(),
                "content",
                Map.of(),
                "INDEXED",
                1,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                null
        );
        when(documentPersistenceService.findReviewDocument(userId, "source-1")).thenReturn(Optional.of(document));

        ReviewCriterionEntity criterion = new ReviewCriterionEntity();
        criterion.setId(UUID.randomUUID());
        criterion.setCode("LOGIC");
        criterion.setName("Logic");
        criterion.setMaxScore(100);
        criterion.setWeight(20);
        criterion.setEnabled(true);
        criterion.setSortOrder(1);
        when(criterionMapper.selectList(any())).thenReturn(List.of(criterion));
        ReviewAssignmentEntity assignment = assignment(UUID.randomUUID(), taskId, userId);
        when(assignmentMapper.selectActiveByTaskAndReviewer(taskId, userId)).thenReturn(assignment);
        when(paperStructuredParseService.find(userId, "source-1")).thenReturn(Optional.empty());
        when(llmService.generate(any())).thenReturn("not json");
        when(reviewOutputParser.parse("not json")).thenThrow(new IllegalArgumentException("缺少 JSON 对象"));

        ReviewServiceImpl service = new ReviewServiceImpl(
                taskMapper,
                reportMapper,
                assignmentMapper,
                criterionMapper,
                auditLogMapper,
                null,
                null,
                documentPersistenceService,
                paperStructuredParseService,
                llmService,
                null,
                reviewOutputParser,
                new ReferenceFormatChecker(),
                mock(ReviewAuditService.class),
                mock(ReviewRiskService.class),
                objectMapper
        );

        assertThatThrownBy(() -> service.generateAiReview(userId, false, taskId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException response = (ResponseStatusException) ex;
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(response.getReason()).contains("缺少 JSON 对象");
                    assertThat(response.getCause()).isInstanceOf(IllegalArgumentException.class);
                });
    }

    @Test
    void generateAiReviewShouldRejectTaskAssignedToAnotherReviewer() {
        UUID currentUserId = UUID.randomUUID();
        UUID otherReviewerId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        ReviewTaskMapper taskMapper = mock(ReviewTaskMapper.class);
        ReviewReportMapper reportMapper = mock(ReviewReportMapper.class);
        ReviewAssignmentMapper assignmentMapper = mock(ReviewAssignmentMapper.class);
        ReviewCriterionMapper criterionMapper = mock(ReviewCriterionMapper.class);
        DocumentPersistenceService documentPersistenceService = mock(DocumentPersistenceService.class);
        PaperStructuredParseService paperStructuredParseService = mock(PaperStructuredParseService.class);
        LlmService llmService = mock(LlmService.class);
        ReviewServiceImpl service = new ReviewServiceImpl(
                taskMapper,
                reportMapper,
                assignmentMapper,
                criterionMapper,
                mock(ReviewAuditLogMapper.class),
                null,
                null,
                documentPersistenceService,
                paperStructuredParseService,
                llmService,
                null,
                mock(ReviewOutputParser.class),
                new ReferenceFormatChecker(),
                mock(ReviewAuditService.class),
                mock(ReviewRiskService.class),
                new ObjectMapper()
        );
        ReviewTaskEntity task = task(taskId, null);
        task.setSubmitterUserId(currentUserId);
        task.setSourceId("source-1");
        when(taskMapper.selectByIdIncludingDeleted(taskId)).thenReturn(task);
        when(documentPersistenceService.findReviewDocument(currentUserId, "source-1"))
                .thenReturn(Optional.of(document(currentUserId, "source-1")));
        when(criterionMapper.selectList(any())).thenReturn(List.of(criterion()));
        when(assignmentMapper.selectActiveByTaskAndReviewer(taskId, currentUserId)).thenReturn(null);
        when(assignmentMapper.selectByTaskId(taskId))
                .thenReturn(List.of(assignment(UUID.randomUUID(), taskId, otherReviewerId)));

        assertThatThrownBy(() -> service.generateAiReview(currentUserId, false, taskId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(llmService, never()).generate(any());
        verify(reportMapper, never()).insert(org.mockito.ArgumentMatchers.any(ReviewReportEntity.class));
        verify(reportMapper, never()).updateById(org.mockito.ArgumentMatchers.any(ReviewReportEntity.class));
    }

    @Test
    void getStructuredParseShouldUseTaskSubmitterDocumentForAssignedReviewer() {
        UUID submitterUserId = UUID.randomUUID();
        UUID reviewerUserId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        ReviewTaskMapper taskMapper = mock(ReviewTaskMapper.class);
        ReviewAssignmentMapper assignmentMapper = mock(ReviewAssignmentMapper.class);
        DocumentPersistenceService documentPersistenceService = mock(DocumentPersistenceService.class);
        PaperStructuredParseService paperStructuredParseService = mock(PaperStructuredParseService.class);
        ReviewServiceImpl service = new ReviewServiceImpl(
                taskMapper,
                mock(ReviewReportMapper.class),
                assignmentMapper,
                mock(ReviewCriterionMapper.class),
                mock(ReviewAuditLogMapper.class),
                null,
                null,
                documentPersistenceService,
                paperStructuredParseService,
                null,
                null,
                mock(ReviewOutputParser.class),
                new ReferenceFormatChecker(),
                mock(ReviewAuditService.class),
                mock(ReviewRiskService.class),
                new ObjectMapper()
        );
        ReviewTaskEntity task = task(taskId, null);
        task.setDocumentId(documentId);
        task.setSubmitterUserId(submitterUserId);
        task.setSourceId("source-1");
        when(taskMapper.selectByIdIncludingDeleted(taskId)).thenReturn(task);
        when(assignmentMapper.selectActiveByTaskAndReviewer(taskId, reviewerUserId))
                .thenReturn(assignment(UUID.randomUUID(), taskId, reviewerUserId));
        when(documentPersistenceService.findReviewDocument(submitterUserId, "source-1"))
                .thenReturn(Optional.of(document(submitterUserId, "source-1")));
        PaperStructuredParseEntity structuredParse = structuredParse(submitterUserId, documentId, "source-1");
        when(paperStructuredParseService.find(submitterUserId, "source-1"))
                .thenReturn(Optional.of(structuredParse));

        PaperStructuredParseResponse response = service.getStructuredParse(reviewerUserId, false, taskId);

        assertThat(response.sourceId()).isEqualTo("source-1");
        assertThat(response.documentId()).isEqualTo(documentId);
        assertThat(response.mergedResult()).isEqualTo(Map.of("title", "Paper A"));
        verify(documentPersistenceService).findReviewDocument(submitterUserId, "source-1");
        verify(documentPersistenceService, never()).findReviewDocument(reviewerUserId, "source-1");
        verify(paperStructuredParseService).find(submitterUserId, "source-1");
        verify(paperStructuredParseService, never()).find(reviewerUserId, "source-1");
    }

    @Test
    void generateAiReviewShouldMergeReferenceRisksFromStructuredParseWhenModelSectionsLackReferences() {
        UUID currentUserId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        ReviewTaskMapper taskMapper = mock(ReviewTaskMapper.class);
        ReviewReportMapper reportMapper = mock(ReviewReportMapper.class);
        ReviewCriterionMapper criterionMapper = mock(ReviewCriterionMapper.class);
        ReviewAuditLogMapper auditLogMapper = mock(ReviewAuditLogMapper.class);
        ReviewAssignmentMapper assignmentMapper = mock(ReviewAssignmentMapper.class);
        DocumentPersistenceService documentPersistenceService = mock(DocumentPersistenceService.class);
        PaperStructuredParseService paperStructuredParseService = mock(PaperStructuredParseService.class);
        LlmService llmService = mock(LlmService.class);
        ReviewOutputParser reviewOutputParser = mock(ReviewOutputParser.class);
        ReviewRiskService reviewRiskService = mock(ReviewRiskService.class);
        ReviewServiceImpl service = new ReviewServiceImpl(
                taskMapper,
                reportMapper,
                assignmentMapper,
                criterionMapper,
                auditLogMapper,
                null,
                null,
                documentPersistenceService,
                paperStructuredParseService,
                llmService,
                null,
                reviewOutputParser,
                new ReferenceFormatChecker(),
                mock(ReviewAuditService.class),
                reviewRiskService,
                new ObjectMapper()
        );
        ReviewTaskEntity task = task(taskId, null);
        task.setDocumentId(documentId);
        task.setSubmitterUserId(currentUserId);
        task.setSourceId("source-1");
        when(taskMapper.selectByIdIncludingDeleted(taskId)).thenReturn(task);
        DocumentPersistenceService.DocumentDetail document = document(currentUserId, "source-1");
        when(documentPersistenceService.findReviewDocument(currentUserId, "source-1")).thenReturn(Optional.of(document));
        ReviewAssignmentEntity assignment = assignment(UUID.randomUUID(), taskId, currentUserId);
        when(assignmentMapper.selectActiveByTaskAndReviewer(taskId, currentUserId)).thenReturn(assignment);
        ReviewCriterionEntity criterion = criterion();
        when(criterionMapper.selectList(any())).thenReturn(List.of(criterion));
        PaperStructuredParseEntity structuredParse = new PaperStructuredParseEntity();
        structuredParse.setMergedResult(Map.of("references", List.of("[1] Missing publication year")));
        when(paperStructuredParseService.find(currentUserId, "source-1")).thenReturn(Optional.of(structuredParse));
        when(llmService.generate(any())).thenReturn("{}");
        when(reviewOutputParser.parse("{}")).thenReturn(Map.of(
                "paperSections", Map.of("title", "title"),
                "risks", List.of()
        ));
        when(reportMapper.selectByAssignmentId(assignment.getId()))
                .thenReturn(null)
                .thenAnswer(invocation -> {
                    ReviewReportEntity response = report(UUID.randomUUID(), taskId);
                    response.setAssignmentId(assignment.getId());
                    response.setRisks(List.of());
                    return response;
                });

        service.generateAiReview(currentUserId, false, taskId);

        ArgumentCaptor<Object> risksCaptor = ArgumentCaptor.forClass(Object.class);
        verify(reviewRiskService).replaceReportRisks(any(), org.mockito.ArgumentMatchers.eq(taskId), risksCaptor.capture());
        assertThat((List<?>) risksCaptor.getValue()).anySatisfy(item -> {
            Map<?, ?> risk = (Map<?, ?>) item;
            assertThat(risk.get("detector")).isEqualTo("REFERENCE_RULE");
            assertThat(risk.get("type")).isEqualTo("REFERENCE_FORMAT");
        });
    }

    @Test
    void updateReportShouldSetManualDeltaAndAppendAuditDiff() {
        UUID currentUserId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        ReviewTaskMapper taskMapper = mock(ReviewTaskMapper.class);
        ReviewReportMapper reportMapper = mock(ReviewReportMapper.class);
        ReviewAuditLogMapper auditLogMapper = mock(ReviewAuditLogMapper.class);
        ReviewAssignmentMapper assignmentMapper = mock(ReviewAssignmentMapper.class);
        ReviewAuditService reviewAuditService = mock(ReviewAuditService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ReviewServiceImpl service = new ReviewServiceImpl(
                taskMapper,
                reportMapper,
                assignmentMapper,
                null,
                auditLogMapper,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new ReferenceFormatChecker(),
                reviewAuditService,
                mock(ReviewRiskService.class),
                objectMapper
        );
        UUID assignmentId = UUID.randomUUID();
        ReviewReportEntity report = report(reportId, taskId);
        report.setAssignmentId(assignmentId);
        ReviewAssignmentEntity assignment = assignment(assignmentId, taskId, currentUserId);
        report.setScores(List.of(Map.of("code", "LOGIC", "score", 70)));
        report.setComments(Map.of("summary", "old"));
        report.setRisks(List.of(Map.of("type", "old")));
        report.setTotalScore(70);
        report.setFinalRecommendation("REVIEW");
        report.setStatus("AI_GENERATED");
        when(reportMapper.selectById(reportId)).thenReturn(report);
        when(taskMapper.selectByIdIncludingDeleted(taskId)).thenReturn(task(taskId, null));
        when(assignmentMapper.selectById(assignmentId)).thenReturn(assignment);
        when(assignmentMapper.selectActiveByTaskAndReviewer(taskId, currentUserId)).thenReturn(assignment);
        ReviewReportUpdateRequest request = new ReviewReportUpdateRequest(
                null,
                List.of(Map.of("code", "LOGIC", "score", 85)),
                Map.of("summary", "updated"),
                null,
                85,
                "PASS",
                "confirmed"
        );

        service.updateReport(currentUserId, false, reportId, request);

        ArgumentCaptor<ReviewReportEntity> reportCaptor = ArgumentCaptor.forClass(ReviewReportEntity.class);
        ArgumentCaptor<Map<String, Object>> beforeCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, Object>> afterCaptor = ArgumentCaptor.forClass(Map.class);
        InOrder inOrder = inOrder(reportMapper, reviewAuditService);
        inOrder.verify(reportMapper).updateById(reportCaptor.capture());
        inOrder.verify(reviewAuditService).append(
                org.mockito.ArgumentMatchers.eq(taskId),
                org.mockito.ArgumentMatchers.eq(currentUserId),
                org.mockito.ArgumentMatchers.eq("ADJUST_REPORT"),
                anyString(),
                beforeCaptor.capture(),
                afterCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(Map.of())
        );
        assertThat(reportCaptor.getValue().getManualDelta())
                .containsEntry("scoreChanged", true)
                .containsEntry("commentEdited", true)
                .containsEntry("riskOverridden", false)
                .containsEntry("finalRecommendationChanged", true);
        assertThat(beforeCaptor.getValue())
                .containsEntry("reportId", reportId.toString())
                .containsEntry("status", "AI_GENERATED");
        assertThat(afterCaptor.getValue())
                .containsEntry("reportId", reportId.toString())
                .containsEntry("status", "CONFIRMED");
    }

    @Test
    void updateReportShouldRejectTaskAssignedToAnotherReviewer() {
        UUID currentUserId = UUID.randomUUID();
        UUID otherReviewerId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        ReviewTaskMapper taskMapper = mock(ReviewTaskMapper.class);
        ReviewReportMapper reportMapper = mock(ReviewReportMapper.class);
        ReviewRiskService reviewRiskService = mock(ReviewRiskService.class);
        ReviewAuditService reviewAuditService = mock(ReviewAuditService.class);
        ReviewServiceImpl service = serviceWithRiskAccess(taskMapper, reportMapper, reviewRiskService);
        ReviewReportEntity report = report(reportId, taskId);
        report.setReviewerUserId(otherReviewerId);
        when(reportMapper.selectById(reportId)).thenReturn(report);
        when(taskMapper.selectByIdIncludingDeleted(taskId)).thenReturn(task(taskId, otherReviewerId));

        assertThatThrownBy(() -> service.updateReport(currentUserId, false, reportId,
                new ReviewReportUpdateRequest(null, null, null, null, null, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(reportMapper, never()).updateById(org.mockito.ArgumentMatchers.any(ReviewReportEntity.class));
        verify(reviewRiskService, never()).replaceReportRisks(any(), any(), any());
        verify(reviewAuditService, never()).append(any(), any(), anyString(), anyString(), any(), any(), any());
    }

    @Test
    void updateReportShouldRejectReviewerSnapshotWithoutActiveAssignment() {
        UUID currentUserId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        ReviewTaskMapper taskMapper = mock(ReviewTaskMapper.class);
        ReviewReportMapper reportMapper = mock(ReviewReportMapper.class);
        ReviewRiskService reviewRiskService = mock(ReviewRiskService.class);
        ReviewServiceImpl service = serviceWithRiskAccess(taskMapper, reportMapper, reviewRiskService);
        ReviewReportEntity report = report(reportId, taskId);
        report.setReviewerUserId(currentUserId);
        when(reportMapper.selectById(reportId)).thenReturn(report);
        when(taskMapper.selectByIdIncludingDeleted(taskId)).thenReturn(task(taskId, currentUserId));

        assertThatThrownBy(() -> service.updateReport(currentUserId, false, reportId,
                new ReviewReportUpdateRequest(null, null, null, null, null, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(reportMapper, never()).updateById(org.mockito.ArgumentMatchers.any(ReviewReportEntity.class));
        verify(reviewRiskService, never()).replaceReportRisks(any(), any(), any());
    }

    @Test
    void updateReportShouldRejectCancelledAssignment() {
        UUID currentUserId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        ReviewTaskMapper taskMapper = mock(ReviewTaskMapper.class);
        ReviewReportMapper reportMapper = mock(ReviewReportMapper.class);
        ReviewAssignmentMapper assignmentMapper = mock(ReviewAssignmentMapper.class);
        ReviewRiskService reviewRiskService = mock(ReviewRiskService.class);
        ReviewServiceImpl service = serviceWithRiskAccess(taskMapper, reportMapper, assignmentMapper, reviewRiskService);
        ReviewReportEntity report = report(reportId, taskId);
        report.setAssignmentId(assignmentId);
        ReviewAssignmentEntity assignment = assignment(assignmentId, taskId, currentUserId, ReviewAssignmentStatuses.CANCELLED);
        when(reportMapper.selectById(reportId)).thenReturn(report);
        when(taskMapper.selectByIdIncludingDeleted(taskId)).thenReturn(task(taskId, currentUserId));
        when(assignmentMapper.selectById(assignmentId)).thenReturn(assignment);

        assertThatThrownBy(() -> service.updateReport(currentUserId, false, reportId,
                new ReviewReportUpdateRequest(null, null, null, null, null, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verify(reportMapper, never()).updateById(org.mockito.ArgumentMatchers.any(ReviewReportEntity.class));
        verify(reviewRiskService, never()).replaceReportRisks(any(), any(), any());
    }

    @Test
    void updateReportShouldSyncNormalizedRisksWhenRisksProvided() {
        UUID currentUserId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        ReviewTaskMapper taskMapper = mock(ReviewTaskMapper.class);
        ReviewReportMapper reportMapper = mock(ReviewReportMapper.class);
        ReviewRiskService reviewRiskService = mock(ReviewRiskService.class);
        ReviewAssignmentMapper assignmentMapper = mock(ReviewAssignmentMapper.class);
        ReviewServiceImpl service = serviceWithRiskAccess(taskMapper, reportMapper, assignmentMapper, reviewRiskService);
        UUID assignmentId = UUID.randomUUID();
        ReviewReportEntity report = report(reportId, taskId);
        report.setAssignmentId(assignmentId);
        report.setStatus("AI_GENERATED");
        ReviewAssignmentEntity assignment = assignment(assignmentId, taskId, currentUserId);
        when(reportMapper.selectById(reportId)).thenReturn(report);
        when(taskMapper.selectByIdIncludingDeleted(taskId)).thenReturn(task(taskId, null));
        when(assignmentMapper.selectById(assignmentId)).thenReturn(assignment);
        when(assignmentMapper.selectActiveByTaskAndReviewer(taskId, currentUserId)).thenReturn(assignment);
        List<Map<String, Object>> updatedRisks = List.of(Map.of("type", "REFERENCE_FORMAT", "level", "MEDIUM"));
        ReviewReportUpdateRequest request = new ReviewReportUpdateRequest(
                null,
                null,
                null,
                updatedRisks,
                null,
                null,
                null
        );

        service.updateReport(currentUserId, false, reportId, request);

        verify(reviewRiskService).replaceReportRisks(reportId, taskId, updatedRisks);
    }

    @Test
    void listRisksShouldRejectTaskAssignedToAnotherReviewer() {
        UUID currentUserId = UUID.randomUUID();
        UUID otherReviewerId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        ReviewTaskMapper taskMapper = mock(ReviewTaskMapper.class);
        ReviewReportMapper reportMapper = mock(ReviewReportMapper.class);
        ReviewRiskService riskService = mock(ReviewRiskService.class);
        ReviewServiceImpl service = serviceWithRiskAccess(taskMapper, reportMapper, riskService);
        when(reportMapper.selectById(reportId)).thenReturn(report(reportId, taskId));
        when(taskMapper.selectByIdIncludingDeleted(taskId)).thenReturn(task(taskId, otherReviewerId));

        assertThatThrownBy(() -> service.listRisks(currentUserId, false, reportId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void listRisksShouldAllowAdminForAssignedTask() {
        UUID currentUserId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        ReviewTaskMapper taskMapper = mock(ReviewTaskMapper.class);
        ReviewReportMapper reportMapper = mock(ReviewReportMapper.class);
        ReviewRiskService riskService = mock(ReviewRiskService.class);
        ReviewServiceImpl service = serviceWithRiskAccess(taskMapper, reportMapper, riskService);
        when(reportMapper.selectById(reportId)).thenReturn(report(reportId, taskId));
        when(taskMapper.selectByIdIncludingDeleted(taskId)).thenReturn(task(taskId, UUID.randomUUID()));
        when(riskService.listByReportId(reportId)).thenReturn(List.of());

        assertThat(service.listRisks(currentUserId, true, reportId)).isEmpty();

        verify(riskService).listByReportId(reportId);
    }

    @Test
    void updateRiskShouldRejectUnassignedTask() {
        UUID currentUserId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID riskId = UUID.randomUUID();
        ReviewTaskMapper taskMapper = mock(ReviewTaskMapper.class);
        ReviewReportMapper reportMapper = mock(ReviewReportMapper.class);
        ReviewRiskService riskService = mock(ReviewRiskService.class);
        ReviewServiceImpl service = serviceWithRiskAccess(taskMapper, reportMapper, riskService);
        ReviewRiskItemEntity risk = new ReviewRiskItemEntity();
        risk.setId(riskId);
        risk.setReportId(reportId);
        risk.setTaskId(taskId);
        when(riskService.findById(riskId)).thenReturn(risk);
        when(reportMapper.selectById(reportId)).thenReturn(report(reportId, taskId));
        ReviewRiskUpdateRequest request = new ReviewRiskUpdateRequest("CONFIRMED", "ok");

        assertThatThrownBy(() -> service.updateRisk(currentUserId, false, riskId, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(riskService, never()).updateStatus(riskId, "CONFIRMED", "ok");
    }

    @Test
    void listTasksForReviewerUsesAssignmentStatusAndCurrentActiveAssignment() {
        UUID currentUserId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        ReviewTaskMapper taskMapper = mock(ReviewTaskMapper.class);
        ReviewReportMapper reportMapper = mock(ReviewReportMapper.class);
        ReviewAssignmentMapper assignmentMapper = mock(ReviewAssignmentMapper.class);
        ReviewServiceImpl service = serviceWithRiskAccess(taskMapper, reportMapper, assignmentMapper, mock(ReviewRiskService.class));
        ReviewTaskEntity task = task(taskId, null);
        task.setStatus(ReviewTaskStatuses.IN_REVIEW);
        ReviewAssignmentEntity assignment = assignment(assignmentId, taskId, currentUserId, ReviewAssignmentStatuses.REVIEWING);
        when(taskMapper.selectReviewerTasks(currentUserId, null, ReviewAssignmentStatuses.REVIEWING)).thenReturn(List.of(task));
        when(assignmentMapper.selectActiveByTaskAndReviewer(taskId, currentUserId)).thenReturn(assignment);
        when(reportMapper.selectByAssignmentId(assignmentId)).thenReturn(report(UUID.randomUUID(), taskId));

        var response = service.listTasks(currentUserId, false, null, ReviewTaskStatuses.IN_REVIEW, 0, 20);

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().currentAssignment()).isNotNull();
        assertThat(response.items().getFirst().currentAssignment().id()).isEqualTo(assignmentId);
        assertThat(response.items().getFirst().currentAssignment().status()).isEqualTo(ReviewAssignmentStatuses.REVIEWING);
        verify(taskMapper).selectReviewerTasks(currentUserId, null, ReviewAssignmentStatuses.REVIEWING);
    }

    @Test
    void getTaskShouldRejectReviewerSnapshotWithoutActiveAssignment() {
        UUID currentUserId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        ReviewTaskMapper taskMapper = mock(ReviewTaskMapper.class);
        ReviewAssignmentMapper assignmentMapper = mock(ReviewAssignmentMapper.class);
        ReviewServiceImpl service = serviceWithRiskAccess(taskMapper, mock(ReviewReportMapper.class), assignmentMapper, mock(ReviewRiskService.class));
        when(taskMapper.selectByIdIncludingDeleted(taskId)).thenReturn(task(taskId, currentUserId));
        when(assignmentMapper.selectActiveByTaskAndReviewer(taskId, currentUserId)).thenReturn(null);

        assertThatThrownBy(() -> service.getTask(currentUserId, false, taskId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void listRisksShouldRejectCancelledAssignmentReport() {
        UUID currentUserId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        ReviewReportMapper reportMapper = mock(ReviewReportMapper.class);
        ReviewAssignmentMapper assignmentMapper = mock(ReviewAssignmentMapper.class);
        ReviewRiskService riskService = mock(ReviewRiskService.class);
        ReviewServiceImpl service = serviceWithRiskAccess(mock(ReviewTaskMapper.class), reportMapper, assignmentMapper, riskService);
        ReviewReportEntity report = report(reportId, taskId);
        report.setAssignmentId(assignmentId);
        ReviewAssignmentEntity assignment = assignment(assignmentId, taskId, currentUserId, ReviewAssignmentStatuses.CANCELLED);
        when(reportMapper.selectById(reportId)).thenReturn(report);
        when(assignmentMapper.selectById(assignmentId)).thenReturn(assignment);
        when(assignmentMapper.selectActiveByTaskAndReviewer(taskId, currentUserId)).thenReturn(null);

        assertThatThrownBy(() -> service.listRisks(currentUserId, false, reportId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(riskService, never()).listByReportId(reportId);
    }

    private PromptConstructionService.Prompt buildReviewPrompt(ReviewServiceImpl service,
                                                                  DocumentPersistenceService.DocumentDetail document,
                                                                  List<ReviewCriterionResponse> criteria) throws Exception {
        Method method = ReviewServiceImpl.class.getDeclaredMethod("buildReviewPrompt", DocumentPersistenceService.DocumentDetail.class, List.class);
        method.setAccessible(true);
        return (PromptConstructionService.Prompt) method.invoke(service, document, criteria);
    }

    private ReviewServiceImpl serviceWithRiskAccess(ReviewTaskMapper taskMapper,
                                                    ReviewReportMapper reportMapper,
                                                    ReviewRiskService riskService) {
        return serviceWithRiskAccess(taskMapper, reportMapper, mock(ReviewAssignmentMapper.class), riskService);
    }

    private ReviewServiceImpl serviceWithRiskAccess(ReviewTaskMapper taskMapper,
                                                    ReviewReportMapper reportMapper,
                                                    ReviewAssignmentMapper assignmentMapper,
                                                    ReviewRiskService riskService) {
        return new ReviewServiceImpl(
                taskMapper,
                reportMapper,
                assignmentMapper,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new ReferenceFormatChecker(),
                mock(ReviewAuditService.class),
                riskService,
                new ObjectMapper()
        );
    }

    private ReviewServiceImpl serviceWithDependencies(ReviewTaskMapper taskMapper,
                                                      ReviewReportMapper reportMapper,
                                                      ReviewCriterionMapper criterionMapper,
                                                      ReviewAuditLogMapper auditLogMapper,
                                                      com.lqr.papermind.document.mapper.DocumentMapper documentMapper,
                                                      DocumentPersistenceService documentPersistenceService,
                                                      PaperStructuredParseService paperStructuredParseService,
                                                      LlmService llmService,
                                                      ReviewOutputParser reviewOutputParser,
                                                      ReferenceFormatChecker referenceFormatChecker,
                                                      ReviewRiskService riskService,
                                                      ReviewAuditService auditService) {
        return new ReviewServiceImpl(
                taskMapper,
                reportMapper,
                mock(ReviewAssignmentMapper.class),
                criterionMapper,
                auditLogMapper,
                null,
                documentMapper,
                documentPersistenceService,
                paperStructuredParseService,
                llmService,
                null,
                reviewOutputParser,
                referenceFormatChecker,
                auditService,
                riskService,
                new ObjectMapper()
        );
    }

    private ReviewCriterionEntity criterion() {
        ReviewCriterionEntity criterion = new ReviewCriterionEntity();
        criterion.setId(UUID.randomUUID());
        criterion.setCode("LOGIC");
        criterion.setName("Logic");
        criterion.setMaxScore(100);
        criterion.setWeight(20);
        criterion.setEnabled(true);
        criterion.setSortOrder(1);
        return criterion;
    }

    private DocumentPersistenceService.DocumentDetail document(UUID ownerUserId, String sourceId) {
        return new DocumentPersistenceService.DocumentDetail(
                sourceId,
                ownerUserId,
                "title",
                null,
                null,
                null,
                null,
                List.of(),
                "abstract",
                null,
                null,
                2024,
                List.of(),
                "content",
                Map.of(),
                "INDEXED",
                1,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                null
        );
    }

    private PaperStructuredParseEntity structuredParse(UUID ownerUserId, UUID documentId, String sourceId) {
        PaperStructuredParseEntity entity = new PaperStructuredParseEntity();
        entity.setId(UUID.randomUUID());
        entity.setOwnerUserId(ownerUserId);
        entity.setDocumentId(documentId);
        entity.setSourceId(sourceId);
        entity.setStatus("COMPLETED");
        entity.setMergedResult(Map.of("title", "Paper A"));
        entity.setMissingFields(List.of());
        entity.setLowConfidenceFields(List.of());
        entity.setParsedAt(OffsetDateTime.now());
        entity.setUpdatedAt(OffsetDateTime.now());
        return entity;
    }

    private ReviewReportEntity report(UUID reportId, UUID taskId) {
        ReviewReportEntity report = new ReviewReportEntity();
        report.setId(reportId);
        report.setTaskId(taskId);
        return report;
    }

    private ReviewTaskEntity task(UUID taskId, UUID reviewerUserId) {
        ReviewTaskEntity task = new ReviewTaskEntity();
        task.setId(taskId);
        task.setReviewerUserId(reviewerUserId);
        return task;
    }

    private ReviewAssignmentEntity assignment(UUID assignmentId, UUID taskId, UUID reviewerUserId) {
        return assignment(assignmentId, taskId, reviewerUserId, ReviewAssignmentStatuses.ASSIGNED);
    }

    private ReviewAssignmentEntity assignment(UUID assignmentId, UUID taskId, UUID reviewerUserId, String status) {
        ReviewAssignmentEntity assignment = new ReviewAssignmentEntity();
        assignment.setId(assignmentId);
        assignment.setTaskId(taskId);
        assignment.setReviewerUserId(reviewerUserId);
        assignment.setStatus(status);
        return assignment;
    }

}
