package com.lqr.paperragserver.document;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.paperragserver.common.model.DocumentChunk;
import com.lqr.paperragserver.document.entity.DocumentChunkEntity;
import com.lqr.paperragserver.document.entity.DocumentEntity;
import com.lqr.paperragserver.document.service.impl.DocumentPersistenceServiceImpl;
import com.lqr.paperragserver.document.mapper.DocumentAssetMapper;
import com.lqr.paperragserver.document.mapper.DocumentChunkMapper;
import com.lqr.paperragserver.document.mapper.DocumentMapper;
import com.lqr.paperragserver.document.service.DocumentPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档持久化服务的数据查询、转换和状态更新测试。
 */
class DocumentPersistenceServiceImplTest {

    private final DocumentMapper documentMapper = mock(DocumentMapper.class);
    private final DocumentChunkMapper chunkMapper = mock(DocumentChunkMapper.class);
    private final DocumentAssetMapper assetMapper = mock(DocumentAssetMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID ownerUserId = UUID.randomUUID();
    private DocumentPersistenceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DocumentPersistenceServiceImpl(documentMapper, chunkMapper, assetMapper, objectMapper);
    }

    @Test
    void listDocumentsShouldExcludeDeletedByDefaultAndSearchKeyword() {
        Page<DocumentEntity> page = new Page<>(1, 20, 0);
        when(documentMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        DocumentPersistenceService.PageResult<DocumentPersistenceService.DocumentSummary> result =
                service.listDocuments(ownerUserId, "paper", null, 0, 20);

        ArgumentCaptor<Page<DocumentEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(documentMapper).selectPage(pageCaptor.capture(), any(LambdaQueryWrapper.class));

        assertThat(result.total()).isZero();
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(20);
    }

    @Test
    void listDocumentsShouldRespectExplicitStatusAndPageOffset() {
        DocumentEntity entity = new DocumentEntity();
        entity.setSourceId("source-1");
        entity.setTitle("Paper A");
        entity.setStatus("INDEXED");
        entity.setChunkCount(3);
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setUpdatedAt(OffsetDateTime.now());
        Page<DocumentEntity> page = new Page<>(3, 10, 1);
        page.setRecords(List.of(entity));
        when(documentMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        DocumentPersistenceService.PageResult<DocumentPersistenceService.DocumentSummary> result =
                service.listDocuments(ownerUserId, null, "indexed", 2, 10);

        ArgumentCaptor<Page<DocumentEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(documentMapper).selectPage(pageCaptor.capture(), any(LambdaQueryWrapper.class));

        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(3);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(10);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items()).singleElement().extracting(DocumentPersistenceService.DocumentSummary::sourceId)
                .isEqualTo("source-1");
    }

    @Test
    void updateMetadataShouldSerializeJsonFieldsAndMergeMetadata() {
        DocumentPersistenceService.DocumentMetadataUpdate update = new DocumentPersistenceService.DocumentMetadataUpdate(
                "Paper A",
                List.of("Alice", "Bob"),
                "abstract",
                "10.1000/demo",
                "Journal",
                2024,
                List.of("rag", "paper"),
                Map.of("source", "manual")
        );

        when(documentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(userDocument("source-1"));

        service.updateMetadata(ownerUserId, "source-1", update);

        ArgumentCaptor<String> authorsCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keywordsCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> metadataCaptor = ArgumentCaptor.forClass(String.class);
        verify(documentMapper).updateMetadata(
                org.mockito.ArgumentMatchers.eq(ownerUserId),
                org.mockito.ArgumentMatchers.eq("source-1"),
                org.mockito.ArgumentMatchers.eq("Paper A"),
                authorsCaptor.capture(),
                org.mockito.ArgumentMatchers.eq("abstract"),
                org.mockito.ArgumentMatchers.eq("10.1000/demo"),
                org.mockito.ArgumentMatchers.eq("Journal"),
                org.mockito.ArgumentMatchers.eq(2024),
                keywordsCaptor.capture(),
                metadataCaptor.capture()
        );

        assertThat(authorsCaptor.getValue()).contains("Alice", "Bob");
        assertThat(keywordsCaptor.getValue()).contains("rag", "paper");
        assertThat(metadataCaptor.getValue()).contains("manual");
    }

    @Test
    void replaceChunksShouldTrimSectionTitleToDatabaseLimit() {
        String longSectionTitle = "A".repeat(600);
        DocumentChunk chunk = new DocumentChunk(
                "chunk-1",
                "source-1",
                0,
                "chunk text",
                Map.of("sectionTitle", longSectionTitle)
        );

        service.replaceChunks(ownerUserId, "source-1", List.of(chunk));

        ArgumentCaptor<DocumentChunkEntity> entityCaptor = ArgumentCaptor.forClass(DocumentChunkEntity.class);
        verify(chunkMapper).insert(entityCaptor.capture());

        assertThat(entityCaptor.getValue().getSectionTitle())
                .hasSize(512)
                .isEqualTo(longSectionTitle.substring(0, 512));
        assertThat(entityCaptor.getValue().getContentHash()).hasSize(64);
    }

    @Test
    void replaceChunksShouldAssignPrimaryKeyBeforeInsert() {
        DocumentChunk chunk = new DocumentChunk(
                "chunk-1",
                "source-1",
                0,
                "chunk text",
                Map.of()
        );

        service.replaceChunks(ownerUserId, "source-1", List.of(chunk));

        ArgumentCaptor<DocumentChunkEntity> entityCaptor = ArgumentCaptor.forClass(DocumentChunkEntity.class);
        verify(chunkMapper).insert(entityCaptor.capture());

        assertThat(entityCaptor.getValue().getId()).isNotNull();
    }

    @Test
    void replaceAssetsShouldAssignPrimaryKeyBeforeInsert() {
        com.lqr.paperragserver.common.model.DocumentAsset asset = new com.lqr.paperragserver.common.model.DocumentAsset(
                "asset-1",
                "source-1",
                0,
                "IMAGE",
                "image.png",
                "image/png",
                3L,
                "hash",
                new byte[]{1, 2, 3},
                "ocr text",
                0,
                8,
                Map.of()
        );

        service.replaceAssets(ownerUserId, "source-1", List.of(asset));

        ArgumentCaptor<com.lqr.paperragserver.document.entity.DocumentAssetEntity> entityCaptor = ArgumentCaptor.forClass(com.lqr.paperragserver.document.entity.DocumentAssetEntity.class);
        verify(assetMapper).insert(entityCaptor.capture());

        assertThat(entityCaptor.getValue().getId()).isNotNull();
    }

    @Test
    void markIndexedShouldIgnoreDeletedDocument() {
        service.markIndexed(ownerUserId, "source-1", 12);

        verify(documentMapper).markIndexed(ownerUserId, "source-1", 12);
    }

    @Test
    void restoreShouldOnlyRestoreDeletedDocumentAsPending() {
        when(documentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(userDocument("source-1"));

        service.restore(ownerUserId, "source-1");

        verify(documentMapper).restore(ownerUserId, "source-1");
    }

    private DocumentEntity userDocument(String sourceId) {
        DocumentEntity entity = new DocumentEntity();
        entity.setOwnerUserId(ownerUserId);
        entity.setSourceId(sourceId);
        entity.setTitle("Paper A");
        entity.setMetadata(Map.of("sourceType", "USER"));
        return entity;
    }
}