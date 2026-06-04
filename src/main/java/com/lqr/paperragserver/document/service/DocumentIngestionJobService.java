package com.lqr.paperragserver.document.service;

import com.lqr.paperragserver.document.entity.DocumentIngestionJob;

import java.util.Map;
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

    /**
     * 创建文档入库任务并持久化到数据库。
     *
     * @param jobId 任务 ID
     * @param ownerUserId 文档所属用户 ID
     * @param sourceId 文档来源标识
     * @param fileName 原始文件名
     * @param filePath 上传文件存储路径
     * @param title 文档标题
     * @return 创建的入库任务实体
     */
    DocumentIngestionJob createJob(UUID jobId,
                                   UUID ownerUserId,
                                   String sourceId,
                                   String fileName,
                                   String filePath,
                                   String title,
                                   Map<String, Object> metadata);

    /**
     * 将任务标记为已入队状态。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param jobId 任务 ID
     */
    void markQueued(UUID ownerUserId, UUID jobId);

    /**
     * 抢占任务处理权，仅当任务处于可处理状态时成功。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param jobId 任务 ID
     * @return 抢占成功返回 true，否则返回 false
     */
    boolean claimForProcessing(UUID ownerUserId, UUID jobId);

    /**
     * 标记任务当前运行阶段和进度。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param jobId 任务 ID
     * @param sourceId 文档来源标识
     * @param status 当前阶段状态
     * @param progress 进度百分比（0-100）
     */
    void markRunningStage(UUID ownerUserId, UUID jobId, String sourceId, String status, int progress);

    /**
     * 标记任务索引完成。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param jobId 任务 ID
     * @param sourceId 文档来源标识
     */
    void markIndexed(UUID ownerUserId, UUID jobId, String sourceId);

    /**
     * 标记任务处理失败并记录错误信息。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param jobId 任务 ID
     * @param sourceId 文档来源标识
     * @param errorMessage 错误信息
     */
    void markFailed(UUID ownerUserId, UUID jobId, String sourceId, String errorMessage);

    /**
     * 增加任务重试计数。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param jobId 任务 ID
     * @return 更新后的重试次数
     */
    int incrementRetry(UUID ownerUserId, UUID jobId);

    /**
     * 查询指定用户的入库任务。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param jobId 任务 ID
     * @return 入库任务实体，不存在时返回空
     */
    Optional<DocumentIngestionJob> findJob(UUID ownerUserId, UUID jobId);
}