package com.lqr.paperragserver.document.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.paperragserver.auth.security.SecurityUserPrincipal;
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
    private final ObjectMapper objectMapper;

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
            DocumentIngestionJob job = createAndPublishJob(ownerUserId, file, sourceId, title, null);
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
                DocumentIngestionJob job = createAndPublishJob(principal.getId(), file, item.sourceId(), item.title(), fileName);
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

    @GetMapping("/jobs/{jobId}")
    public DocumentJobResponse job(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                   @PathVariable UUID jobId) {
        return documentIngestionJobService.findJob(principal.getId(), jobId)
                .map(DocumentJobResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
    }

    @GetMapping("/{sourceId}")
    public DocumentDetailResponse detail(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                         @PathVariable String sourceId) {
        return documentPersistenceService.findDocument(principal.getId(), sourceId)
                .map(DocumentDetailResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文档不存在"));
    }

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

    @GetMapping("/{sourceId}/assets")
    public List<DocumentAssetResponse> assets(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                              @PathVariable String sourceId,
                                              @RequestParam(value = "assetIds", required = false) String assetIds) {
        return documentPersistenceService.listAssets(principal.getId(), sourceId, parseAssetIds(assetIds)).stream()
                .map(DocumentAssetResponse::from)
                .toList();
    }

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

    @PatchMapping("/{sourceId}/metadata")
    public DocumentDetailResponse updateMetadata(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                 @PathVariable String sourceId,
                                                 @Valid @RequestBody DocumentMetadataRequest request) {
        documentPersistenceService.updateMetadata(principal.getId(), sourceId, request.toUpdate());
        return detail(principal, sourceId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAll(@AuthenticationPrincipal SecurityUserPrincipal principal) {
        documentIngestionService.deleteAll(principal.getId());
    }

    @DeleteMapping("/{sourceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBySourceId(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                 @PathVariable String sourceId) {
        documentIngestionService.deleteBySourceId(principal.getId(), sourceId);
    }

    @PostMapping("/{sourceId}/restore")
    public DocumentDetailResponse restore(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                          @PathVariable String sourceId) {
        documentManagementService.restore(principal.getId(), sourceId);
        return detail(principal, sourceId);
    }

    @PostMapping("/{sourceId}/reindex")
    public ReindexResponse reindex(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                   @PathVariable String sourceId) {
        DocumentManagementService.ReindexResult result = documentManagementService.reindex(principal.getId(), sourceId);
        return new ReindexResponse(result.sourceId(), result.chunkCount());
    }

    private DocumentIngestionJob createAndPublishJob(UUID ownerUserId,
                                                     MultipartFile file,
                                                     String sourceId,
                                                     String title,
                                                     String fallbackFileName) throws IOException {
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
                title
        );
        documentIngestionProducer.publish(new DocumentIngestionMessage(job.getId(), ownerUserId, resolvedSourceId));
        return documentIngestionJobService.findJob(ownerUserId, job.getId()).orElse(job);
    }

    private List<String> parseAssetIds(String assetIds) {
        if (assetIds == null || assetIds.isBlank()) {
            return List.of();
        }
        return Arrays.stream(assetIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

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

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

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