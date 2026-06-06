package com.lqr.paperragserver.document.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.paperragserver.auth.security.SecurityUserPrincipal;
import com.lqr.paperragserver.common.constant.MetadataKeys;
import com.lqr.paperragserver.document.dto.BatchDocumentIngestionItemRequest;
import com.lqr.paperragserver.document.dto.BatchDocumentIngestionItemResponse;
import com.lqr.paperragserver.document.dto.BatchDocumentIngestionResponse;
import com.lqr.paperragserver.document.dto.DocumentAssetResponse;
import com.lqr.paperragserver.document.dto.DocumentChunkResponse;
import com.lqr.paperragserver.document.dto.DocumentDetailResponse;
import com.lqr.paperragserver.document.dto.DocumentJobResponse;
import com.lqr.paperragserver.document.dto.DocumentMetadataRequest;
import com.lqr.paperragserver.document.dto.DocumentSummaryResponse;
import com.lqr.paperragserver.document.dto.DocumentUploadAcceptedResponse;
import com.lqr.paperragserver.document.dto.PageResponse;
import com.lqr.paperragserver.document.dto.ReindexResponse;
import com.lqr.paperragserver.document.entity.DocumentIngestionJob;
import com.lqr.paperragserver.document.model.DocumentIngestionMessage;
import com.lqr.paperragserver.document.service.DocumentIngestionJobService;
import com.lqr.paperragserver.document.service.DocumentIngestionProducer;
import com.lqr.paperragserver.document.service.DocumentIngestionService;
import com.lqr.paperragserver.document.service.DocumentManagementService;
import com.lqr.paperragserver.document.service.DocumentPersistenceService;
import com.lqr.paperragserver.document.service.DocumentUploadStorageService;
import com.lqr.paperragserver.document.structured.dto.PaperStructuredParseResponse;
import com.lqr.paperragserver.document.structured.dto.PaperStructuredParseStatusResponse;
import com.lqr.paperragserver.document.structured.service.PaperStructuredParseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文档入库与管理接口。
 */
@Slf4j
@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentIngestionService documentIngestionService;
    private final DocumentManagementService documentManagementService;
    private final DocumentPersistenceService documentPersistenceService;
    private final DocumentIngestionJobService documentIngestionJobService;
    private final DocumentUploadStorageService documentUploadStorageService;
    private final DocumentIngestionProducer documentIngestionProducer;
    private final PaperStructuredParseService paperStructuredParseService;
    private final ObjectMapper objectMapper;

    /**
     * 上传单个文档并异步入库。
     *
     * @param principal 当前登录用户
     * @param file 上传的文件
     * @param sourceId 文档来源标识（可选）
     * @param title 文档标题（可选）
     * @return 202 Accepted，包含入库任务信息
     * @throws IOException 文件读取失败时抛出
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentUploadAcceptedResponse> ingest(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                                 @RequestParam("file") MultipartFile file,
                                                                 @RequestParam(value = "sourceId", required = false) String sourceId,
                                                                 @RequestParam(value = "title", required = false) String title) throws IOException {
        long startNanos = System.nanoTime();
        UUID ownerUserId = principal.getId();
        String fileName = file.getOriginalFilename();
        log.info("document.upload.start ownerUserId={} sourceId={} fileName={} fileType={} fileSize={}",
                ownerUserId, sourceId, fileName, file.getContentType(), file.getSize());
        try {
            DocumentIngestionJob job = createAndPublishJob(ownerUserId, file, sourceId, title, null, MetadataKeys.SOURCE_TYPE_USER);
            log.info("document.upload.done ownerUserId={} jobId={} sourceId={} fileName={} fileType={} fileSize={} costMs={}",
                    ownerUserId, job.getId(), job.getSourceId(), job.getFileName(), file.getContentType(), file.getSize(), elapsedMs(startNanos));
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(DocumentUploadAcceptedResponse.from(job));
        } catch (IOException | RuntimeException ex) {
            log.error("document.upload.failed ownerUserId={} sourceId={} fileName={} fileType={} fileSize={} costMs={}",
                    ownerUserId, sourceId, fileName, file.getContentType(), file.getSize(), elapsedMs(startNanos), ex);
            throw ex;
        }
    }

    /**
     * 批量上传文档并异步入库。
     *
     * @param principal 当前登录用户
     * @param files 上传的文件数组
     * @param items 批量上传项参数（JSON 格式，可选）
     * @return 202 Accepted，包含每个文件的处理结果
     */
    @PostMapping(path = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BatchDocumentIngestionResponse> ingestBatch(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                                      @RequestParam("files") MultipartFile[] files,
                                                                      @RequestParam(value = "items", required = false) String items) {
        if (files == null || files.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请至少上传一个文件");
        }

        List<BatchDocumentIngestionItemRequest> requests = parseBatchItems(items, files.length);
        List<BatchDocumentIngestionItemResponse> results = new java.util.ArrayList<>(files.length);
        int acceptedCount = 0;

        for (int index = 0; index < files.length; index++) {
            MultipartFile file = files[index];
            BatchDocumentIngestionItemRequest item = requests.get(index);
            String fileName = originalFileName(file, item);
            long itemStartNanos = System.nanoTime();
            log.info("document.upload.start ownerUserId={} sourceId={} fileName={} fileType={} fileSize={} batchIndex={} batchSize={}",
                    principal.getId(), item.sourceId(), fileName, file.getContentType(), file.getSize(), index, files.length);
            try {
                DocumentIngestionJob job = createAndPublishJob(principal.getId(), file, item.sourceId(), item.title(), fileName, MetadataKeys.SOURCE_TYPE_USER);
                log.info("document.upload.done ownerUserId={} jobId={} sourceId={} fileName={} fileType={} fileSize={} batchIndex={} batchSize={} costMs={}",
                        principal.getId(), job.getId(), job.getSourceId(), job.getFileName(), file.getContentType(), file.getSize(), index, files.length, elapsedMs(itemStartNanos));
                results.add(BatchDocumentIngestionItemResponse.accepted(index, fileName, job));
                acceptedCount++;
            } catch (Exception ex) {
                log.error("document.upload.failed ownerUserId={} sourceId={} fileName={} fileType={} fileSize={} batchIndex={} batchSize={} costMs={}",
                        principal.getId(), item.sourceId(), fileName, file.getContentType(), file.getSize(), index, files.length, elapsedMs(itemStartNanos), ex);
                String errorMessage = ex.getMessage() == null || ex.getMessage().isBlank() ? "上传失败" : ex.getMessage();
                results.add(BatchDocumentIngestionItemResponse.failure(index, fileName, errorMessage));
            }
        }

        log.info("document.upload.batch.done ownerUserId={} totalCount={} acceptedCount={} failedCount={}",
                principal.getId(), files.length, acceptedCount, files.length - acceptedCount);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new BatchDocumentIngestionResponse(results, acceptedCount, files.length - acceptedCount));
    }

    /**
     * 分页查询当前用户的文档摘要列表。
     *
     * @param principal 当前登录用户
     * @param keyword 关键词过滤（可选）
     * @param status 状态过滤（可选）
     * @param page 页码（从 0 开始）
     * @param size 每页条数
     * @return 分页文档摘要响应
     */
    @GetMapping
    public PageResponse<DocumentSummaryResponse> list(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                      @RequestParam(value = "keyword", required = false) String keyword,
                                                      @RequestParam(value = "status", required = false) String status,
                                                      @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
                                                      @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        DocumentPersistenceService.PageResult<DocumentPersistenceService.DocumentSummary> result =
                documentPersistenceService.listDocuments(principal.getId(), keyword, status, page, size);
        return new PageResponse<>(
                result.items().stream().map(DocumentSummaryResponse::from).toList(),
                result.page(),
                result.size(),
                result.total()
        );
    }

    /**
     * 查询指定入库任务的状态。
     *
     * @param principal 当前登录用户
     * @param jobId 任务 ID
     * @return 任务状态响应
     */
    @GetMapping("/jobs/{jobId}")
    public DocumentJobResponse job(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                   @PathVariable UUID jobId) {
        return documentIngestionJobService.findJob(principal.getId(), jobId)
                .map(DocumentJobResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
    }

    /**
     * 查询指定文档的详情。
     *
     * @param principal 当前登录用户
     * @param sourceId 文档来源标识
     * @return 文档详情响应
     */
    @GetMapping("/{sourceId}")
    public DocumentDetailResponse detail(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                         @PathVariable String sourceId) {
        return documentPersistenceService.findDocument(principal.getId(), sourceId)
                .map(DocumentDetailResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文档不存在"));
    }

    /**
     * 查询指定论文的结构化解析结果。
     */
    @GetMapping("/{sourceId}/structured-parse")
    public PaperStructuredParseResponse structuredParse(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                        @PathVariable String sourceId) {
        ensureAnyDocumentExists(principal.getId(), sourceId);
        return paperStructuredParseService.find(principal.getId(), sourceId)
                .map(PaperStructuredParseResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "结构化解析结果不存在"));
    }

    /**
     * 查询指定论文的结构化解析状态。
     */
    @GetMapping("/{sourceId}/structured-parse/status")
    public PaperStructuredParseStatusResponse structuredParseStatus(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                                    @PathVariable String sourceId) {
        ensureAnyDocumentExists(principal.getId(), sourceId);
        return paperStructuredParseService.find(principal.getId(), sourceId)
                .map(PaperStructuredParseStatusResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "结构化解析结果不存在"));
    }

    /**
     * 重新生成指定论文的结构化解析结果。
     */
    @PostMapping("/{sourceId}/structured-parse/regenerate")
    public PaperStructuredParseResponse regenerateStructuredParse(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                                  @PathVariable String sourceId) {
        ensureAnyDocumentExists(principal.getId(), sourceId);
        return PaperStructuredParseResponse.from(paperStructuredParseService.regenerate(principal.getId(), sourceId));
    }

    /**
     * 分页查询指定文档的分块列表。
     *
     * @param principal 当前登录用户
     * @param sourceId 文档来源标识
     * @param page 页码（从 0 开始）
     * @param size 每页条数
     * @return 分页分块响应
     */
    @GetMapping("/{sourceId}/chunks")
    public PageResponse<DocumentChunkResponse> chunks(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                      @PathVariable String sourceId,
                                                      @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
                                                      @RequestParam(value = "size", defaultValue = "50") @Min(1) @Max(200) int size) {
        DocumentPersistenceService.PageResult<DocumentPersistenceService.DocumentChunkView> result =
                documentPersistenceService.listChunks(principal.getId(), sourceId, page, size);
        return new PageResponse<>(
                result.items().stream().map(DocumentChunkResponse::from).toList(),
                result.page(),
                result.size(),
                result.total()
        );
    }

    /**
     * 查询指定文档的资产列表，可按资产 ID 过滤。
     *
     * @param principal 当前登录用户
     * @param sourceId 文档来源标识
     * @param assetIds 逗号分隔的资产 ID（可选）
     * @return 资产响应列表
     */
    @GetMapping("/{sourceId}/assets")
    public List<DocumentAssetResponse> assets(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                              @PathVariable String sourceId,
                                              @RequestParam(value = "assetIds", required = false) String assetIds) {
        return documentPersistenceService.listAssets(principal.getId(), sourceId, parseAssetIds(assetIds)).stream()
                .map(DocumentAssetResponse::from)
                .toList();
    }

    /**
     * 下载指定文档资产的二进制内容。
     *
     * @param principal 当前登录用户
     * @param sourceId 文档来源标识
     * @param assetId 资产 ID
     * @return 资产二进制内容响应
     */
    @GetMapping("/{sourceId}/assets/{assetId}/content")
    public ResponseEntity<byte[]> assetContent(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                               @PathVariable String sourceId,
                                               @PathVariable String assetId) {
        DocumentPersistenceService.DocumentAssetView asset = documentPersistenceService.findAsset(principal.getId(), sourceId, assetId)
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
     * 更新指定文档的可编辑元数据。
     *
     * @param principal 当前登录用户
     * @param sourceId 文档来源标识
     * @param request 元数据更新请求
     * @return 更新后的文档详情
     */
    @PatchMapping("/{sourceId}/metadata")
    public DocumentDetailResponse updateMetadata(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                 @PathVariable String sourceId,
                                                 @Valid @RequestBody DocumentMetadataRequest request) {
        documentPersistenceService.updateMetadata(principal.getId(), sourceId, request.toUpdate());
        return detail(principal, sourceId);
    }

    /**
     * 删除当前用户的全部文档。
     *
     * @param principal 当前登录用户
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAll(@AuthenticationPrincipal SecurityUserPrincipal principal) {
        documentIngestionService.deleteAll(principal.getId());
    }

    /**
     * 删除指定来源标识的文档。
     *
     * @param principal 当前登录用户
     * @param sourceId 文档来源标识
     */
    @DeleteMapping("/{sourceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBySourceId(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                 @PathVariable String sourceId) {
        documentIngestionService.deleteBySourceId(principal.getId(), sourceId);
    }

    /**
     * 恢复已删除的文档。
     *
     * @param principal 当前登录用户
     * @param sourceId 文档来源标识
     * @return 恢复后的文档详情
     */
    @PostMapping("/{sourceId}/restore")
    public DocumentDetailResponse restore(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                          @PathVariable String sourceId) {
        documentManagementService.restore(principal.getId(), sourceId);
        return detail(principal, sourceId);
    }

    /**
     * 重建指定文档的分块和向量索引。
     *
     * @param principal 当前登录用户
     * @param sourceId 文档来源标识
     * @return 重建索引结果
     */
    @PostMapping("/{sourceId}/reindex")
    public ReindexResponse reindex(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                   @PathVariable String sourceId) {
        DocumentManagementService.ReindexResult result = documentManagementService.reindex(principal.getId(), sourceId);
        return new ReindexResponse(result.sourceId(), result.chunkCount());
    }

    /**
     * 确认当前用户拥有该来源文档，兼容普通文档与评审文档。
     */
    private void ensureAnyDocumentExists(UUID ownerUserId, String sourceId) {
        documentPersistenceService.findAnyDocument(ownerUserId, sourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文档不存在"));
    }

    /**
     * 创建入库任务、存储上传文件并发布消息到队列。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param file 上传的文件
     * @param sourceId 文档来源标识
     * @param title 文档标题
     * @param fallbackFileName 备用文件名
     * @return 创建的入库任务实体
     * @throws IOException 文件存储失败时抛出
     */
    private DocumentIngestionJob createAndPublishJob(UUID ownerUserId,
                                                     MultipartFile file,
                                                     String sourceId,
                                                     String title,
                                                     String fallbackFileName,
                                                     String sourceType) throws IOException {
        UUID jobId = UUID.randomUUID();
        String resolvedSourceId = sourceId == null || sourceId.isBlank() ? UUID.randomUUID().toString() : sourceId.trim();
        String fileName = fallbackFileName == null || fallbackFileName.isBlank() ? file.getOriginalFilename() : fallbackFileName;
        DocumentUploadStorageService.StoredUpload upload = documentUploadStorageService.store(
                ownerUserId,
                resolvedSourceId,
                jobId,
                file,
                fileName
        );
        DocumentIngestionJob job = documentIngestionJobService.createJob(
                jobId,
                ownerUserId,
                resolvedSourceId,
                upload.fileName(),
                upload.filePath(),
                title,
                Map.of(MetadataKeys.SOURCE_TYPE, sourceType == null || sourceType.isBlank() ? MetadataKeys.SOURCE_TYPE_USER : sourceType)
        );
        documentIngestionProducer.publish(new DocumentIngestionMessage(job.getId(), ownerUserId, resolvedSourceId));
        return documentIngestionJobService.findJob(ownerUserId, job.getId()).orElse(job);
    }

    /**
     * 解析逗号分隔的资产 ID 字符串为列表。
     *
     * @param assetIds 逗号分隔的资产 ID 字符串
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
     * 解析批量上传的 items 参数，校验数量与文件数一致。
     *
     * @param items JSON 格式的批量上传项参数
     * @param fileCount 上传文件数量
     * @return 批量上传项请求列表
     * @throws ResponseStatusException 参数格式错误或数量不一致时抛出
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
     * 计算从指定起始时间到当前的耗时（毫秒）。
     *
     * @param startNanos 起始时间（纳秒）
     * @return 耗时毫秒数
     */
    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    /**
     * 获取上传文件的原始文件名，优先使用文件自带名称，其次使用请求中指定的名称。
     *
     * @param file 上传的文件
     * @param item 批量上传项请求
     * @return 文件名
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
}