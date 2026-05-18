package com.lqr.paperragserver.paper.service;

import com.lqr.paperragserver.paper.entity.DocumentIngestionJob;

import java.util.Optional;
import java.util.UUID;

/**
 * 文档异步入库任务服务。
 */
public interface DocumentIngestionJobService {

    String STATUS_PENDING = "PENDING";
    String STATUS_QUEUED = "QUEUED";
    String STATUS_PARSING = "PARSING";
    String STATUS_CHUNKING = "CHUNKING";
    String STATUS_EMBEDDING = "EMBEDDING";
    String STATUS_INDEXING = "INDEXING";
    String STATUS_INDEXED = "INDEXED";
    String STATUS_FAILED = "FAILED";

    DocumentIngestionJob createJob(UUID jobId, UUID ownerUserId, String sourceId, String fileName, String filePath, String title);

    void markQueued(UUID ownerUserId, UUID jobId);

    boolean claimForProcessing(UUID ownerUserId, UUID jobId);

    void markRunningStage(UUID ownerUserId, UUID jobId, String sourceId, String status, int progress);

    void markIndexed(UUID ownerUserId, UUID jobId, String sourceId);

    void markFailed(UUID ownerUserId, UUID jobId, String sourceId, String errorMessage);

    int incrementRetry(UUID ownerUserId, UUID jobId);

    Optional<DocumentIngestionJob> findJob(UUID ownerUserId, UUID jobId);
}