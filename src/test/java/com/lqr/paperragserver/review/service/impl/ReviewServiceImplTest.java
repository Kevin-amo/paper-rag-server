package com.lqr.paperragserver.review.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.paperragserver.ai.service.LlmService;
import com.lqr.paperragserver.document.service.DocumentPersistenceService;
import com.lqr.paperragserver.document.structured.service.PaperStructuredParseService;
import com.lqr.paperragserver.review.assessment.ReviewOutputParser;
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
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
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
import static org.mockito.Mockito.mock;
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
                objectMapper
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
                objectMapper
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
        ReviewServiceImpl service = new ReviewServiceImpl(
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
                new ObjectMapper()
        );
        ReflectionTestUtils.setField(service, "reviewRiskService", riskService);
        return service;
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
