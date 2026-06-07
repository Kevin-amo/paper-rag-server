package com.lqr.paperragserver.review.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.paperragserver.ai.service.LlmService;
import com.lqr.paperragserver.document.service.DocumentPersistenceService;
import com.lqr.paperragserver.document.structured.entity.PaperStructuredParseEntity;
import com.lqr.paperragserver.document.structured.service.PaperStructuredParseService;
import com.lqr.paperragserver.review.assessment.ReviewOutputParser;
import com.lqr.paperragserver.review.audit.ReviewAuditService;
import com.lqr.paperragserver.review.dto.ReviewReportUpdateRequest;
import com.lqr.paperragserver.review.dto.ReviewRiskItemResponse;
import com.lqr.paperragserver.review.dto.ReviewRiskUpdateRequest;
import com.lqr.paperragserver.review.entity.ReviewCriterionEntity;
import com.lqr.paperragserver.review.entity.ReviewReportEntity;
import com.lqr.paperragserver.review.entity.ReviewRiskItemEntity;
import com.lqr.paperragserver.review.entity.ReviewTaskEntity;
import com.lqr.paperragserver.review.mapper.ReviewAuditLogMapper;
import com.lqr.paperragserver.review.mapper.ReviewCriterionMapper;
import com.lqr.paperragserver.review.mapper.ReviewReportMapper;
import com.lqr.paperragserver.review.mapper.ReviewTaskMapper;
import com.lqr.paperragserver.review.risk.ReferenceFormatChecker;
import com.lqr.paperragserver.review.risk.ReviewRiskService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
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
                new ReviewOutputParser(objectMapper),
                new ReferenceFormatChecker(),
                objectMapper,
                mock(ReviewAuditService.class),
                mock(ReviewRiskService.class)
        );

        assertThat(service).isNotNull();
    }

    @Test
    void generateAiReviewShouldWrapParserErrorsAsBadGateway() {
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        ReviewTaskMapper taskMapper = mock(ReviewTaskMapper.class);
        ReviewReportMapper reportMapper = mock(ReviewReportMapper.class);
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
        when(paperStructuredParseService.find(userId, "source-1")).thenReturn(Optional.empty());
        when(llmService.generate(any())).thenReturn("not json");
        when(reviewOutputParser.parse("not json")).thenThrow(new IllegalArgumentException("\u7f3a\u5c11 JSON \u5bf9\u8c61"));

        ReviewServiceImpl service = new ReviewServiceImpl(
                taskMapper,
                reportMapper,
                criterionMapper,
                auditLogMapper,
                null,
                documentPersistenceService,
                paperStructuredParseService,
                llmService,
                reviewOutputParser,
                new ReferenceFormatChecker(),
                objectMapper,
                mock(ReviewAuditService.class),
                mock(ReviewRiskService.class)
        );

        assertThatThrownBy(() -> service.generateAiReview(userId, false, taskId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException response = (ResponseStatusException) ex;
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(response.getReason()).contains("\u7f3a\u5c11 JSON \u5bf9\u8c61");
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
        LlmService llmService = mock(LlmService.class);
        ReviewServiceImpl service = serviceWithDependencies(
                taskMapper,
                reportMapper,
                null,
                null,
                null,
                null,
                mock(PaperStructuredParseService.class),
                llmService,
                mock(ReviewOutputParser.class),
                new ReferenceFormatChecker(),
                mock(ReviewRiskService.class),
                mock(ReviewAuditService.class)
        );
        when(taskMapper.selectByIdIncludingDeleted(taskId)).thenReturn(task(taskId, otherReviewerId));

        assertThatThrownBy(() -> service.generateAiReview(currentUserId, false, taskId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(llmService, never()).generate(any());
        verify(reportMapper, never()).insert(org.mockito.ArgumentMatchers.any(ReviewReportEntity.class));
        verify(reportMapper, never()).updateById(org.mockito.ArgumentMatchers.any(ReviewReportEntity.class));
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
        DocumentPersistenceService documentPersistenceService = mock(DocumentPersistenceService.class);
        PaperStructuredParseService paperStructuredParseService = mock(PaperStructuredParseService.class);
        LlmService llmService = mock(LlmService.class);
        ReviewOutputParser reviewOutputParser = mock(ReviewOutputParser.class);
        ReviewRiskService reviewRiskService = mock(ReviewRiskService.class);
        ReviewServiceImpl service = serviceWithDependencies(
                taskMapper,
                reportMapper,
                criterionMapper,
                auditLogMapper,
                null,
                documentPersistenceService,
                paperStructuredParseService,
                llmService,
                reviewOutputParser,
                new ReferenceFormatChecker(),
                reviewRiskService,
                mock(ReviewAuditService.class)
        );
        ReviewTaskEntity task = task(taskId, null);
        task.setDocumentId(documentId);
        task.setSubmitterUserId(currentUserId);
        task.setSourceId("source-1");
        when(taskMapper.selectByIdIncludingDeleted(taskId)).thenReturn(task);
        DocumentPersistenceService.DocumentDetail document = document(currentUserId, "source-1");
        when(documentPersistenceService.findReviewDocument(currentUserId, "source-1")).thenReturn(Optional.of(document));
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
        when(reportMapper.selectLatestByTaskId(taskId))
                .thenReturn(null)
                .thenAnswer(invocation -> {
                    ReviewReportEntity response = report(UUID.randomUUID(), taskId);
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
        ReviewAuditService reviewAuditService = mock(ReviewAuditService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ReviewServiceImpl service = new ReviewServiceImpl(
                taskMapper,
                reportMapper,
                null,
                auditLogMapper,
                null,
                null,
                null,
                null,
                null,
                new ReferenceFormatChecker(),
                objectMapper,
                reviewAuditService,
                mock(ReviewRiskService.class)
        );
        ReviewReportEntity report = report(reportId, taskId);
        report.setScores(List.of(Map.of("code", "LOGIC", "score", 70)));
        report.setComments(Map.of("summary", "old"));
        report.setRisks(List.of(Map.of("type", "old")));
        report.setTotalScore(70);
        report.setFinalRecommendation("REVIEW");
        report.setStatus("AI_GENERATED");
        when(reportMapper.selectById(reportId)).thenReturn(report);
        when(taskMapper.selectByIdIncludingDeleted(taskId)).thenReturn(task(taskId, null));
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
        when(reportMapper.selectById(reportId)).thenReturn(report(reportId, taskId));
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
    void updateReportShouldSyncNormalizedRisksWhenRisksProvided() {
        UUID currentUserId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        ReviewTaskMapper taskMapper = mock(ReviewTaskMapper.class);
        ReviewReportMapper reportMapper = mock(ReviewReportMapper.class);
        ReviewRiskService reviewRiskService = mock(ReviewRiskService.class);
        ReviewServiceImpl service = serviceWithRiskAccess(taskMapper, reportMapper, reviewRiskService);
        ReviewReportEntity report = report(reportId, taskId);
        report.setStatus("AI_GENERATED");
        when(reportMapper.selectById(reportId)).thenReturn(report);
        when(taskMapper.selectByIdIncludingDeleted(taskId)).thenReturn(task(taskId, null));
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
    void updateRiskShouldAllowUnassignedTask() {
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
        when(taskMapper.selectByIdIncludingDeleted(taskId)).thenReturn(task(taskId, null));
        ReviewRiskUpdateRequest request = new ReviewRiskUpdateRequest("CONFIRMED", "ok");
        ReviewRiskItemResponse response = ReviewRiskItemResponse.from(risk);
        when(riskService.updateStatus(riskId, "CONFIRMED", "ok")).thenReturn(response);

        assertThat(service.updateRisk(currentUserId, false, riskId, request)).isEqualTo(response);

        verify(riskService).updateStatus(riskId, "CONFIRMED", "ok");
    }

    private ReviewServiceImpl serviceWithRiskAccess(ReviewTaskMapper taskMapper,
                                                    ReviewReportMapper reportMapper,
                                                    ReviewRiskService riskService) {
        ReviewServiceImpl service = serviceWithDependencies(
                taskMapper,
                reportMapper,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new ReferenceFormatChecker(),
                riskService,
                mock(ReviewAuditService.class)
        );
        return service;
    }

    private ReviewServiceImpl serviceWithDependencies(ReviewTaskMapper taskMapper,
                                                      ReviewReportMapper reportMapper,
                                                      ReviewCriterionMapper criterionMapper,
                                                      ReviewAuditLogMapper auditLogMapper,
                                                      com.lqr.paperragserver.document.mapper.DocumentMapper documentMapper,
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
                criterionMapper,
                auditLogMapper,
                documentMapper,
                documentPersistenceService,
                paperStructuredParseService,
                llmService,
                reviewOutputParser,
                referenceFormatChecker,
                new ObjectMapper(),
                auditService,
                riskService
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

}
