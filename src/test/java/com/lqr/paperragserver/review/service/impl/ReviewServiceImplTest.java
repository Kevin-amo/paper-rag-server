package com.lqr.paperragserver.review.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.paperragserver.ai.service.LlmService;
import com.lqr.paperragserver.document.service.DocumentPersistenceService;
import com.lqr.paperragserver.document.structured.service.PaperStructuredParseService;
import com.lqr.paperragserver.review.assessment.ReviewOutputParser;
import com.lqr.paperragserver.review.entity.ReviewCriterionEntity;
import com.lqr.paperragserver.review.entity.ReviewTaskEntity;
import com.lqr.paperragserver.review.mapper.ReviewAuditLogMapper;
import com.lqr.paperragserver.review.mapper.ReviewCriterionMapper;
import com.lqr.paperragserver.review.mapper.ReviewReportMapper;
import com.lqr.paperragserver.review.mapper.ReviewTaskMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReviewServiceImplTest {

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
}
