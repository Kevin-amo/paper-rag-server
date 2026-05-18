package com.lqr.paperragserver.paper.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lqr.paperragserver.common.constant.MetadataKeys;
import com.lqr.paperragserver.common.model.DocumentSource;
import com.lqr.paperragserver.paper.entity.DocumentIngestionJob;
import com.lqr.paperragserver.paper.mapper.DocumentIngestionJobMapper;
import com.lqr.paperragserver.paper.service.DocumentIngestionJobService;
import com.lqr.paperragserver.paper.service.PaperDocumentPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 基于数据库的文档异步入库任务服务。
 */
@Service
@RequiredArgsConstructor
public class DocumentIngestionJobServiceImpl implements DocumentIngestionJobService {

    private static final int ERROR_MESSAGE_MAX_LENGTH = 4000;

    private final DocumentIngestionJobMapper jobMapper;
    private final PaperDocumentPersistenceService paperDocumentPersistenceService;

    @Override
    @Transactional
    public DocumentIngestionJob createJob(UUID jobId, UUID ownerUserId, String sourceId, String fileName, String filePath, String title) {
        String safeTitle = hasText(title) ? title.trim() : fileName;
        DocumentIngestionJob job = new DocumentIngestionJob();
        job.setId(jobId);
        job.setOwnerUserId(ownerUserId);
        job.setSourceId(sourceId);
        job.setFileName(fileName);
        job.setFilePath(filePath);
        job.setTitle(safeTitle);
        job.setStatus(STATUS_PENDING);
        job.setProgress(0);
        job.setRetryCount(0);
        jobMapper.insert(job);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(MetadataKeys.SOURCE_ID, sourceId);
        metadata.put(MetadataKeys.FILE_NAME, fileName);
        metadata.put(MetadataKeys.TITLE, safeTitle);
        paperDocumentPersistenceService.markParsing(
                ownerUserId,
                new DocumentSource(sourceId, safeTitle, fileName, metadata),
                ""
        );
        paperDocumentPersistenceService.markStatus(ownerUserId, sourceId, STATUS_PENDING, 0);
        return job;
    }

    @Override
    @Transactional
    public void markQueued(UUID ownerUserId, UUID jobId) {
        findJob(ownerUserId, jobId).ifPresent(job -> {
            jobMapper.markQueued(ownerUserId, jobId);
            paperDocumentPersistenceService.markStatus(ownerUserId, job.getSourceId(), STATUS_QUEUED, 5);
        });
    }

    @Override
    public boolean claimForProcessing(UUID ownerUserId, UUID jobId) {
        return jobMapper.claimForProcessing(ownerUserId, jobId) > 0;
    }

    @Override
    @Transactional
    public void markRunningStage(UUID ownerUserId, UUID jobId, String sourceId, String status, int progress) {
        int safeProgress = clamp(progress, 0, 100);
        jobMapper.markRunningStage(ownerUserId, jobId, status, safeProgress);
        paperDocumentPersistenceService.markStatus(ownerUserId, sourceId, status, safeProgress);
    }

    @Override
    @Transactional
    public void markIndexed(UUID ownerUserId, UUID jobId, String sourceId) {
        jobMapper.markIndexed(ownerUserId, jobId);
        paperDocumentPersistenceService.markStatus(ownerUserId, sourceId, STATUS_INDEXED, 100);
    }

    @Override
    @Transactional
    public void markFailed(UUID ownerUserId, UUID jobId, String sourceId, String errorMessage) {
        String safeMessage = cut(errorMessage, ERROR_MESSAGE_MAX_LENGTH);
        jobMapper.markFailed(ownerUserId, jobId, safeMessage);
        paperDocumentPersistenceService.markFailed(ownerUserId, sourceId, safeMessage);
    }

    @Override
    public int incrementRetry(UUID ownerUserId, UUID jobId) {
        jobMapper.incrementRetry(ownerUserId, jobId);
        return findJob(ownerUserId, jobId)
                .map(DocumentIngestionJob::getRetryCount)
                .orElse(0);
    }

    @Override
    public Optional<DocumentIngestionJob> findJob(UUID ownerUserId, UUID jobId) {
        return Optional.ofNullable(jobMapper.selectOne(new LambdaQueryWrapper<DocumentIngestionJob>()
                .eq(DocumentIngestionJob::getOwnerUserId, ownerUserId)
                .eq(DocumentIngestionJob::getId, jobId)));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String cut(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "文档入库失败";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}