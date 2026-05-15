package com.lqr.paperragserver.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.paperragserver.auth.security.SecurityUserPrincipal;
import com.lqr.paperragserver.common.model.DocumentIngestionResult;
import com.lqr.paperragserver.document.service.DocumentIngestionService;
import com.lqr.paperragserver.document.DocumentManagementService;
import com.lqr.paperragserver.common.constant.MetadataKeys;
import com.lqr.paperragserver.paper.service.PaperDocumentPersistenceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文档入库与管理接口。
 */
@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentIngestionService documentIngestionService;
    private final DocumentManagementService documentManagementService;
    private final PaperDocumentPersistenceService paperDocumentPersistenceService;
    private final ObjectMapper objectMapper;

    /**
     * 上传并入库文档。
     *
     * @param file 待入库文件
     * @param sourceId 可选的外部来源标识
     * @param title 可选标题
     * @return 入库结果
     * @throws IOException 读取上传文件失败时抛出
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentIngestionResult ingest(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                          @RequestParam("file") MultipartFile file,
                                          @RequestParam(value = "sourceId", required = false) String sourceId,
                                          @RequestParam(value = "title", required = false) String title) throws IOException {
        return documentIngestionService.ingest(principal.getId(), file.getOriginalFilename(), file.getBytes(), buildMetadata(sourceId, title));
    }

    /**
     * 批量上传并入库多个文档，单个文件失败不会中断整批处理。
     *
     * @param files 待上传文件数组
     * @param items 与文件一一对应的可选元数据 JSON
     * @return 批量入库结果
     */
    @PostMapping(path = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BatchDocumentIngestionResponse ingestBatch(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                      @RequestParam("files") MultipartFile[] files,
                                                      @RequestParam(value = "items", required = false) String items) {
        if (files == null || files.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请至少上传一个文件");
        }

        List<BatchDocumentIngestionItemRequest> requests = parseBatchItems(items, files.length);
        List<BatchDocumentIngestionItemResponse> results = new java.util.ArrayList<>(files.length);
        int successCount = 0;

        for (int index = 0; index < files.length; index++) {
            MultipartFile file = files[index];
            BatchDocumentIngestionItemRequest item = requests.get(index);
            String fileName = originalFileName(file, item);
            try {
                // 入库
                DocumentIngestionResult result = documentIngestionService.ingest(
                        principal.getId(),
                        fileName,
                        file.getBytes(),
                        buildMetadata(item.sourceId(), item.title())
                );
                results.add(BatchDocumentIngestionItemResponse.success(index, fileName, result));
                successCount++;
            } catch (Exception ex) {
                String errorMessage = ex.getMessage() == null || ex.getMessage().isBlank() ? "上传失败" : ex.getMessage();
                results.add(BatchDocumentIngestionItemResponse.failure(index, fileName, errorMessage));
            }
        }

        return new BatchDocumentIngestionResponse(results, successCount, files.length - successCount);
    }

    /**
     * 分页查询文档摘要列表。
     *
     * @param keyword 可选关键词
     * @param status 可选状态过滤
     * @param page 页码，从 0 开始
     * @param size 每页数量
     * @return 文档摘要分页结果
     */
    @GetMapping
    public PageResponse<DocumentSummaryResponse> list(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                      @RequestParam(value = "keyword", required = false) String keyword,
                                                      @RequestParam(value = "status", required = false) String status,
                                                      @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
                                                      @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        PaperDocumentPersistenceService.PageResult<PaperDocumentPersistenceService.DocumentSummary> result =
                paperDocumentPersistenceService.listDocuments(principal.getId(), keyword, status, page, size);
        return new PageResponse<>(
                result.items().stream().map(DocumentSummaryResponse::from).toList(),
                result.page(),
                result.size(),
                result.total()
        );
    }

    /**
     * 查询单个文档详情。
     *
     * @param sourceId 文档来源 ID
     * @return 文档详情
     */
    @GetMapping("/{sourceId}")
    public DocumentDetailResponse detail(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                         @PathVariable String sourceId) {
        return paperDocumentPersistenceService.findDocument(principal.getId(), sourceId)
                .map(DocumentDetailResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文档不存在"));
    }

    /**
     * 分页查询指定文档的分块内容。
     *
     * @param sourceId 文档来源 ID
     * @param page 页码，从 0 开始
     * @param size 每页数量
     * @return 文档分块分页结果
     */
    @GetMapping("/{sourceId}/chunks")
    public PageResponse<DocumentChunkResponse> chunks(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                      @PathVariable String sourceId,
                                                      @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
                                                      @RequestParam(value = "size", defaultValue = "50") @Min(1) @Max(200) int size) {
        PaperDocumentPersistenceService.PageResult<PaperDocumentPersistenceService.DocumentChunkView> result =
                paperDocumentPersistenceService.listChunks(principal.getId(), sourceId, page, size);
        return new PageResponse<>(
                result.items().stream().map(DocumentChunkResponse::from).toList(),
                result.page(),
                result.size(),
                result.total()
        );
    }

    /**
     * 查询指定文档的资产列表。
     *
     * @param sourceId 文档来源 ID
     * @param assetIds 可选的逗号分隔资产 ID 列表
     * @return 文档资产列表
     */
    @GetMapping("/{sourceId}/assets")
    public List<DocumentAssetResponse> assets(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                              @PathVariable String sourceId,
                                              @RequestParam(value = "assetIds", required = false) String assetIds) {
        return paperDocumentPersistenceService.listAssets(principal.getId(), sourceId, parseAssetIds(assetIds)).stream()
                .map(DocumentAssetResponse::from)
                .toList();
    }

    /**
     * 下载指定文档资产的原始内容。
     *
     * @param sourceId 文档来源 ID
     * @param assetId 资产 ID
     * @return 资产二进制响应
     */
    @GetMapping("/{sourceId}/assets/{assetId}/content")
    public ResponseEntity<byte[]> assetContent(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                               @PathVariable String sourceId,
                                               @PathVariable String assetId) {
        PaperDocumentPersistenceService.DocumentAssetView asset = paperDocumentPersistenceService.findAsset(principal.getId(), sourceId, assetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "资产不存在"));
        byte[] content = asset.content() == null ? new byte[0] : asset.content();
        MediaType mediaType = asset.contentType() == null || asset.contentType().isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(asset.contentType());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(content.length)
                .body(content);
    }

    /**
     * 更新指定文档的元数据并返回最新详情。
     *
     * @param sourceId 文档来源 ID
     * @param request 元数据更新请求
     * @return 更新后的文档详情
     */
    @PatchMapping("/{sourceId}/metadata")
    public DocumentDetailResponse updateMetadata(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                 @PathVariable String sourceId,
                                                 @Valid @RequestBody DocumentMetadataRequest request) {
        paperDocumentPersistenceService.updateMetadata(principal.getId(), sourceId, request.toUpdate());
        return detail(principal, sourceId);
    }

    /**
     * 删除指定来源文档的向量数据和分片数据，并软删除文档主记录。
     *
     * @param sourceId 文档来源标识
     */
    @DeleteMapping("/{sourceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBySourceId(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                 @PathVariable String sourceId) {
        documentIngestionService.deleteBySourceId(principal.getId(), sourceId);
    }

    /**
     * 恢复已删除文档并返回最新详情。
     *
     * @param sourceId 文档来源 ID
     * @return 恢复后的文档详情
     */
    @PostMapping("/{sourceId}/restore")
    public DocumentDetailResponse restore(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                          @PathVariable String sourceId) {
        documentManagementService.restore(principal.getId(), sourceId);
        return detail(principal, sourceId);
    }

    /**
     * 重新构建指定文档的分块和向量索引。
     *
     * @param sourceId 文档来源 ID
     * @return 重建索引结果
     */
    @PostMapping("/{sourceId}/reindex")
    public ReindexResponse reindex(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                   @PathVariable String sourceId) {
        DocumentManagementService.ReindexResult result = documentManagementService.reindex(principal.getId(), sourceId);
        return new ReindexResponse(result.sourceId(), result.chunkCount());
    }

    /**
     * 构建入库时传递给解析流程的基础元数据。
     *
     * @param sourceId 可选来源 ID
     * @param title 可选标题
     * @return 元数据映射
     */
    private Map<String, Object> buildMetadata(String sourceId, String title) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (sourceId != null && !sourceId.isBlank()) {
            metadata.put(MetadataKeys.SOURCE_ID, sourceId);
        }
        if (title != null && !title.isBlank()) {
            metadata.put(MetadataKeys.TITLE, title);
        }
        return metadata;
    }

    /**
     * 将逗号分隔的资产 ID 参数解析为列表。
     *
     * @param assetIds 逗号分隔的资产 ID
     * @return 资产 ID 列表
     */
    private List<String> parseAssetIds(String assetIds) {
        if (assetIds == null || assetIds.isBlank()) {
            return List.of();
        }
        return Arrays.stream(assetIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    /**
     * 解析批量上传中与文件一一对应的元数据项。
     *
     * @param items 元数据 JSON 字符串
     * @param fileCount 文件数量
     * @return 批量上传项请求列表
     */
    private List<BatchDocumentIngestionItemRequest> parseBatchItems(String items, int fileCount) {
        if (items == null || items.isBlank()) {
            return java.util.stream.IntStream.range(0, fileCount)
                    .mapToObj(index -> new BatchDocumentIngestionItemRequest(null, null, null))
                    .toList();
        }
        try {
            List<BatchDocumentIngestionItemRequest> requests = objectMapper.readValue(
                    items,
                    new TypeReference<List<BatchDocumentIngestionItemRequest>>() {
                    }
            );
            if (requests.size() != fileCount) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "items 数量必须与文件数量一致");
            }
            return requests.stream()
                    .map(item -> item == null ? new BatchDocumentIngestionItemRequest(null, null, null) : item)
                    .toList();
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "items 参数不是合法 JSON", ex);
        }
    }

    /**
     * 解析上传文件名，缺失时回退到批量项中的文件名。
     *
     * @param file 上传文件
     * @param item 批量上传项元数据
     * @return 可用于入库的文件名
     */
    private String originalFileName(MultipartFile file, BatchDocumentIngestionItemRequest item) {
        String fileName = file.getOriginalFilename();
        if (fileName != null && !fileName.isBlank()) {
            return fileName;
        }
        if (item != null && item.fileName() != null && !item.fileName().isBlank()) {
            return item.fileName();
        }
        return "unknown";
    }

    public record BatchDocumentIngestionResponse(
            List<BatchDocumentIngestionItemResponse> items,
            int successCount,
            int failureCount
    ) {
    }

    public record BatchDocumentIngestionItemResponse(
            int index,
            String fileName,
            boolean success,
            String errorMessage,
            DocumentSourceResponse source,
            Integer chunkCount
    ) {
        /**
         * 构建批量入库成功项响应。
         *
         * @param index 文件序号
         * @param fileName 文件名
         * @param result 入库结果
         * @return 成功项响应
         */
        static BatchDocumentIngestionItemResponse success(int index, String fileName, DocumentIngestionResult result) {
            return new BatchDocumentIngestionItemResponse(
                    index,
                    fileName,
                    true,
                    null,
                    DocumentSourceResponse.from(result.source()),
                    result.chunkCount()
            );
        }

        /**
         * 构建批量入库失败项响应。
         *
         * @param index 文件序号
         * @param fileName 文件名
         * @param errorMessage 错误信息
         * @return 失败项响应
         */
        static BatchDocumentIngestionItemResponse failure(int index, String fileName, String errorMessage) {
            return new BatchDocumentIngestionItemResponse(index, fileName, false, errorMessage, null, null);
        }
    }

    public record BatchDocumentIngestionItemRequest(String fileName, String sourceId, String title) {
    }

    public record DocumentSourceResponse(
            String sourceId,
            String title,
            String origin,
            Map<String, Object> metadata
    ) {
        /**
         * 将内部文档来源对象转换为接口响应。
         *
         * @param source 文档来源对象
         * @return 文档来源响应
         */
        static DocumentSourceResponse from(com.lqr.paperragserver.common.model.DocumentSource source) {
            return new DocumentSourceResponse(source.sourceId(), source.title(), source.origin(), source.metadata());
        }
    }

    public record PageResponse<T>(List<T> items, int page, int size, long total) {
    }

    public record DocumentSummaryResponse(
            String sourceId,
            UUID ownerUserId,
            String title,
            String origin,
            String fileName,
            String fileType,
            Long fileSize,
            String status,
            int chunkCount,
            Integer publishYear,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        /**
         * 将持久化层文档摘要转换为接口响应。
         *
         * @param document 文档摘要
         * @return 文档摘要响应
         */
        static DocumentSummaryResponse from(PaperDocumentPersistenceService.DocumentSummary document) {
            return new DocumentSummaryResponse(
                    document.sourceId(),
                    document.ownerUserId(),
                    document.title(),
                    document.origin(),
                    document.fileName(),
                    document.fileType(),
                    document.fileSize(),
                    document.status(),
                    document.chunkCount(),
                    document.publishYear(),
                    document.createdAt(),
                    document.updatedAt()
            );
        }
    }

    public record DocumentDetailResponse(
            String sourceId,
            UUID ownerUserId,
            String title,
            String origin,
            String fileName,
            String fileType,
            Long fileSize,
            Object authors,
            String abstractText,
            String doi,
            String journal,
            Integer publishYear,
            Object keywords,
            String contentText,
            Map<String, Object> metadata,
            String status,
            int chunkCount,
            String errorMessage,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime deletedAt
    ) {
        /**
         * 将持久化层文档详情转换为接口响应。
         *
         * @param document 文档详情
         * @return 文档详情响应
         */
        static DocumentDetailResponse from(PaperDocumentPersistenceService.DocumentDetail document) {
            return new DocumentDetailResponse(
                    document.sourceId(),
                    document.ownerUserId(),
                    document.title(),
                    document.origin(),
                    document.fileName(),
                    document.fileType(),
                    document.fileSize(),
                    document.authors(),
                    document.abstractText(),
                    document.doi(),
                    document.journal(),
                    document.publishYear(),
                    document.keywords(),
                    document.contentText(),
                    document.metadata(),
                    document.status(),
                    document.chunkCount(),
                    document.errorMessage(),
                    document.createdAt(),
                    document.updatedAt(),
                    document.deletedAt()
            );
        }
    }

    public record DocumentChunkResponse(
            String chunkId,
            UUID ownerUserId,
            int chunkIndex,
            String content,
            String contentHash,
            Integer chunkStart,
            Integer chunkEnd,
            Integer pageNumber,
            String sectionTitle,
            Map<String, Object> metadata,
            UUID vectorStoreId,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        /**
         * 将持久化层分块视图转换为接口响应。
         *
         * @param chunk 分块视图
         * @return 分块响应
         */
        static DocumentChunkResponse from(PaperDocumentPersistenceService.DocumentChunkView chunk) {
            return new DocumentChunkResponse(
                    chunk.chunkId(),
                    chunk.ownerUserId(),
                    chunk.chunkIndex(),
                    chunk.content(),
                    chunk.contentHash(),
                    chunk.chunkStart(),
                    chunk.chunkEnd(),
                    chunk.pageNumber(),
                    chunk.sectionTitle(),
                    chunk.metadata(),
                    chunk.vectorStoreId(),
                    chunk.createdAt(),
                    chunk.updatedAt()
            );
        }
    }

    public record DocumentAssetResponse(
            String assetId,
            String sourceId,
            UUID ownerUserId,
            int assetIndex,
            String assetType,
            String fileName,
            String contentType,
            Long fileSize,
            String contentHash,
            String extractedText,
            Integer textStart,
            Integer textEnd,
            Map<String, Object> metadata,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        /**
         * 将持久化层资产视图转换为接口响应。
         *
         * @param asset 资产视图
         * @return 资产响应
         */
        static DocumentAssetResponse from(PaperDocumentPersistenceService.DocumentAssetView asset) {
            return new DocumentAssetResponse(
                    asset.assetId(),
                    asset.sourceId(),
                    asset.ownerUserId(),
                    asset.assetIndex(),
                    asset.assetType(),
                    asset.fileName(),
                    asset.contentType(),
                    asset.fileSize(),
                    asset.contentHash(),
                    asset.extractedText(),
                    asset.textStart(),
                    asset.textEnd(),
                    asset.metadata(),
                    asset.createdAt(),
                    asset.updatedAt()
            );
        }
    }

    public record DocumentMetadataRequest(
            String title,
            Object authors,
            String abstractText,
            String doi,
            String journal,
            Integer publishYear,
            Object keywords,
            Map<String, Object> metadata
    ) {
        /**
         * 转换为持久化层元数据更新对象。
         *
         * @return 元数据更新对象
         */
        PaperDocumentPersistenceService.DocumentMetadataUpdate toUpdate() {
            return new PaperDocumentPersistenceService.DocumentMetadataUpdate(
                    title,
                    authors,
                    abstractText,
                    doi,
                    journal,
                    publishYear,
                    keywords,
                    metadata
            );
        }
    }

    public record ReindexResponse(String sourceId, int chunkCount) {
    }
}