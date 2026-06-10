package com.lqr.papermind.document.structured;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.papermind.document.entity.DocumentEntity;
import com.lqr.papermind.document.mapper.DocumentMapper;
import com.lqr.papermind.document.service.DocumentPersistenceService;
import com.lqr.papermind.document.structured.entity.PaperStructuredParseEntity;
import com.lqr.papermind.document.structured.mapper.PaperStructuredParseMapper;
import com.lqr.papermind.document.structured.model.ModelCompletionResult;
import com.lqr.papermind.document.structured.model.PaperStructuredContentSupport;
import com.lqr.papermind.document.structured.model.StructuredParseResult;
import com.lqr.papermind.document.structured.service.PaperSectionRuleParser;
import com.lqr.papermind.document.structured.service.PaperStructuredMergePolicy;
import com.lqr.papermind.document.structured.service.PaperStructuredModelCompleter;
import com.lqr.papermind.document.structured.service.impl.PaperStructuredParseServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 论文结构化解析编排服务测试。
 */
class PaperStructuredParseServiceTest {

    private final PaperStructuredParseMapper structuredParseMapper = mock(PaperStructuredParseMapper.class);
    private final DocumentMapper documentMapper = mock(DocumentMapper.class);
    private final DocumentPersistenceService documentPersistenceService = mock(DocumentPersistenceService.class);
    private final PaperSectionRuleParser ruleParser = mock(PaperSectionRuleParser.class);
    private final PaperStructuredModelCompleter modelCompleter = mock(PaperStructuredModelCompleter.class);
    private final PaperStructuredMergePolicy mergePolicy = mock(PaperStructuredMergePolicy.class);
    private PaperStructuredParseServiceImpl service;
    private UUID ownerUserId;

    @BeforeEach
    void setUp() {
        service = new PaperStructuredParseServiceImpl(
                structuredParseMapper,
                documentMapper,
                documentPersistenceService,
                ruleParser,
                modelCompleter,
                mergePolicy,
                new ObjectMapper()
        );
        ownerUserId = UUID.randomUUID();
    }

    @Test
    void generateAndRegenerateShouldRunOutsideCallerTransaction() throws Exception {
        assertNotSupportedTransaction("generate");
        assertNotSupportedTransaction("regenerate");
    }

    @Test
    void generateShouldPersistFailureWhenResultUpsertFails() {
        DocumentPersistenceService.DocumentDetail document = document("论文标题", "摘要文本", "全文");
        DocumentEntity entity = documentEntity(document);
        StructuredParseResult ruleResult = new StructuredParseResult(PaperStructuredContentSupport.emptyContent(), PaperStructuredContentSupport.emptyEvidence("RULE"), List.of(), List.of());
        StructuredParseResult modelResult = new StructuredParseResult(PaperStructuredContentSupport.emptyContent(), PaperStructuredContentSupport.emptyEvidence("MODEL"), List.of(), List.of());
        PaperStructuredParseEntity failed = new PaperStructuredParseEntity();
        failed.setId(UUID.randomUUID());
        failed.setOwnerUserId(ownerUserId);
        failed.setSourceId("source-1");
        failed.setStatus("FAILED");

        when(documentPersistenceService.findAnyDocument(ownerUserId, "source-1")).thenReturn(Optional.of(document));
        when(documentMapper.selectOne(any(Wrapper.class))).thenReturn(entity);
        when(ruleParser.parse(document)).thenReturn(ruleResult);
        when(modelCompleter.complete(document, ruleResult)).thenReturn(new ModelCompletionResult(modelResult, "{}", null));
        when(mergePolicy.merge(ruleResult, modelResult)).thenReturn(ruleResult);
        when(structuredParseMapper.upsertResult(any(UUID.class), eq(ownerUserId), eq(entity.getId()), eq("source-1"), eq("全文"), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), eq("{}"), eq("COMPLETED"), eq(null)))
                .thenThrow(new IllegalStateException("结果保存失败"));
        when(structuredParseMapper.selectOne(any(Wrapper.class))).thenReturn(failed);

        PaperStructuredParseEntity result = service.generate(ownerUserId, "source-1");

        assertThat(result.getStatus()).isEqualTo("FAILED");
        verify(structuredParseMapper).upsertFailed(any(UUID.class), eq(ownerUserId), eq(entity.getId()), eq("source-1"), eq("全文"), eq("结果保存失败"));
    }

    @Test
    void generateShouldPersistMergedResult() {
        DocumentPersistenceService.DocumentDetail document = document("论文标题", "摘要文本", "全文");
        DocumentEntity entity = documentEntity(document);
        StructuredParseResult ruleResult = new StructuredParseResult(PaperStructuredContentSupport.emptyContent(), PaperStructuredContentSupport.emptyEvidence("RULE"), List.of("conclusion"), List.of());
        StructuredParseResult modelResult = new StructuredParseResult(PaperStructuredContentSupport.emptyContent(), PaperStructuredContentSupport.emptyEvidence("MODEL"), List.of(), List.of());
        StructuredParseResult mergedResult = new StructuredParseResult(PaperStructuredContentSupport.withValue(PaperStructuredContentSupport.emptyContent(), "title", "论文标题"), PaperStructuredContentSupport.emptyEvidence("RULE"), List.of(), List.of());
        PaperStructuredParseEntity saved = new PaperStructuredParseEntity();
        saved.setId(UUID.randomUUID());
        saved.setOwnerUserId(ownerUserId);
        saved.setSourceId("source-1");
        saved.setStatus("COMPLETED");
        saved.setMergedResult(Map.of("title", "论文标题"));

        when(documentPersistenceService.findAnyDocument(ownerUserId, "source-1")).thenReturn(Optional.of(document));
        when(documentMapper.selectOne(any(Wrapper.class))).thenReturn(entity);
        when(ruleParser.parse(document)).thenReturn(ruleResult);
        when(modelCompleter.complete(document, ruleResult)).thenReturn(new ModelCompletionResult(modelResult, "{}", null));
        when(mergePolicy.merge(ruleResult, modelResult)).thenReturn(mergedResult);
        when(structuredParseMapper.selectOne(any(Wrapper.class))).thenReturn(saved);

        PaperStructuredParseEntity result = service.generate(ownerUserId, "source-1");

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        verify(structuredParseMapper).upsertResult(any(UUID.class), eq(ownerUserId), eq(entity.getId()), eq("source-1"), eq("全文"), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), eq("{}"), eq("COMPLETED"), eq(null));
    }

    @Test
    void generateShouldPersistFailureWhenParserFails() {
        DocumentPersistenceService.DocumentDetail document = document("论文标题", "摘要文本", "全文");
        DocumentEntity entity = documentEntity(document);
        PaperStructuredParseEntity failed = new PaperStructuredParseEntity();
        failed.setId(UUID.randomUUID());
        failed.setOwnerUserId(ownerUserId);
        failed.setSourceId("source-1");
        failed.setStatus("FAILED");

        when(documentPersistenceService.findAnyDocument(ownerUserId, "source-1")).thenReturn(Optional.of(document));
        when(documentMapper.selectOne(any(Wrapper.class))).thenReturn(entity);
        when(ruleParser.parse(document)).thenThrow(new IllegalStateException("解析失败"));
        when(structuredParseMapper.selectOne(any(Wrapper.class))).thenReturn(failed);

        PaperStructuredParseEntity result = service.generate(ownerUserId, "source-1");

        assertThat(result.getStatus()).isEqualTo("FAILED");
        verify(structuredParseMapper).upsertFailed(any(UUID.class), eq(ownerUserId), eq(entity.getId()), eq("source-1"), eq("全文"), eq("解析失败"));
    }

    private void assertNotSupportedTransaction(String methodName) throws NoSuchMethodException {
        Transactional transactional = PaperStructuredParseServiceImpl.class
                .getMethod(methodName, UUID.class, String.class)
                .getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.NOT_SUPPORTED);
    }

    private DocumentPersistenceService.DocumentDetail document(String title, String abstractText, String contentText) {
        OffsetDateTime now = OffsetDateTime.now();
        return new DocumentPersistenceService.DocumentDetail(
                "source-1",
                ownerUserId,
                title,
                "paper.pdf",
                "paper.pdf",
                "application/pdf",
                100L,
                List.of(),
                abstractText,
                null,
                null,
                null,
                List.of("关键词"),
                contentText,
                Map.of(),
                "INDEXED",
                1,
                null,
                now,
                now,
                null
        );
    }

    private DocumentEntity documentEntity(DocumentPersistenceService.DocumentDetail document) {
        DocumentEntity entity = new DocumentEntity();
        entity.setId(UUID.randomUUID());
        entity.setOwnerUserId(document.ownerUserId());
        entity.setSourceId(document.sourceId());
        entity.setTitle(document.title());
        return entity;
    }
}