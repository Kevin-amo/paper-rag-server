package com.lqr.paperragserver.document.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lqr.paperragserver.common.constant.MetadataKeys;
import com.lqr.paperragserver.common.model.DocumentSource;
import com.lqr.paperragserver.document.entity.DocumentIngestionJob;
import com.lqr.paperragserver.document.mapper.DocumentIngestionJobMapper;
import com.lqr.paperragserver.document.service.DocumentIngestionJobService;
import com.lqr.paperragserver.document.service.DocumentPersistenceService;
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
    private final DocumentPersistenceService documentPersistenceService;

    /**
     * 创建入库任务并持久化，同时初始化文档解析状态。
     *
     * @param jobId 任务 ID
     * @param ownerUserId 文档所属用户 ID
     * @param sourceId 文档来源标识
     * @param fileName 原始文件名
     * @param filePath 上传文件存储路径
     * @param title 文档标题
     * @return 创建的入库任务实体
     */
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
        documentPersistenceService.markParsing(
                ownerUserId,
                new DocumentSource(sourceId, safeTitle, fileName, metadata),
                ""
        );
        documentPersistenceService.markStatus(ownerUserId, sourceId, STATUS_PENDING, 0);
        return job;
    }

    /**
     * 将任务标记为已入队状态，并同步更新文档处理进度。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param jobId 任务 ID
     */
    @Override
    @Transactional
    public void markQueued(UUID ownerUserId, UUID jobId) {
        findJob(ownerUserId, jobId).ifPresent(job -> {
            jobMapper.markQueued(ownerUserId, jobId);
            documentPersistenceService.markStatus(ownerUserId, job.getSourceId(), STATUS_QUEUED, 5);
        });
    }

    /**
     * 抢占任务处理权，仅当任务处于可处理状态时成功。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param jobId 任务 ID
     * @return 抢占成功返回 true，否则返回 false
     */
    @Override
    public boolean claimForProcessing(UUID ownerUserId, UUID jobId) {
        return jobMapper.claimForProcessing(ownerUserId, jobId) > 0;
    }

    /**
     * 标记任务当前运行阶段和进度，并同步更新文档处理状态。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param jobId 任务 ID
     * @param sourceId 文档来源标识
     * @param status 当前阶段状态
     * @param progress 进度百分比（0-100）
     */
    @Override
    @Transactional
    public void markRunningStage(UUID ownerUserId, UUID jobId, String sourceId, String status, int progress) {
        int safeProgress = clamp(progress, 0, 100);
        jobMapper.markRunningStage(ownerUserId, jobId, status, safeProgress);
        documentPersistenceService.markStatus(ownerUserId, sourceId, status, safeProgress);
    }

    /**
     * 标记任务索引完成，并同步更新文档状态为已索引。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param jobId 任务 ID
     * @param sourceId 文档来源标识
     */
    @Override
    @Transactional
    public void markIndexed(UUID ownerUserId, UUID jobId, String sourceId) {
        jobMapper.markIndexed(ownerUserId, jobId);
        documentPersistenceService.markStatus(ownerUserId, sourceId, STATUS_INDEXED, 100);
    }

    /**
     * 标记任务处理失败，截断过长错误信息后持久化，并同步更新文档失败状态。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param jobId 任务 ID
     * @param sourceId 文档来源标识
     * @param errorMessage 错误信息
     */
    @Override
    @Transactional
    public void markFailed(UUID ownerUserId, UUID jobId, String sourceId, String errorMessage) {
        String safeMessage = cut(errorMessage, ERROR_MESSAGE_MAX_LENGTH);
        jobMapper.markFailed(ownerUserId, jobId, safeMessage);
        documentPersistenceService.markFailed(ownerUserId, sourceId, safeMessage);
    }

    /**
     * 增加任务重试计数，并返回更新后的重试次数。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param jobId 任务 ID
     * @return 更新后的重试次数
     */
    @Override
    public int incrementRetry(UUID ownerUserId, UUID jobId) {
        jobMapper.incrementRetry(ownerUserId, jobId);
        return findJob(ownerUserId, jobId)
                .map(DocumentIngestionJob::getRetryCount)
                .orElse(0);
    }

    /**
     * 查询指定用户的入库任务。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param jobId 任务 ID
     * @return 入库任务实体，不存在时返回空
     */
    @Override
    public Optional<DocumentIngestionJob> findJob(UUID ownerUserId, UUID jobId) {
        return Optional.ofNullable(jobMapper.selectOne(new LambdaQueryWrapper<DocumentIngestionJob>()
                .eq(DocumentIngestionJob::getOwnerUserId, ownerUserId)
                .eq(DocumentIngestionJob::getId, jobId)));
    }

    /**
     * 判断字符串是否非空白。
     *
     * @param value 待判断字符串
     * @return 非空白时返回 true
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 将整数限制在指定闭区间内。
     *
     * @param value 原始值
     * @param min 最小值
     * @param max 最大值
     * @return 限制后的值
     */
    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 截断超出最大长度的字符串，空值时返回默认错误信息。
     *
     * @param value 原始字符串
     * @param maxLength 最大长度
     * @return 截断后的字符串
     */
    private String cut(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "文档入库失败";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}