package com.lqr.paperragserver.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lqr.paperragserver.document.entity.DocumentIngestionJob;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.UUID;

/**
 * 文档异步入库任务数据访问接口，负责任务状态流转更新。
 */
public interface DocumentIngestionJobMapper extends BaseMapper<DocumentIngestionJob> {

    /**
     * 将任务标记为已入队状态。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param jobId 任务 ID
     * @return 更新行数
     */
    @Update("""
            update public.document_ingestion_job
            set status = 'QUEUED', progress = 5, error_message = null, updated_at = now()
            where owner_user_id = #{ownerUserId}
              and id = #{jobId}
              and status in ('PENDING', 'QUEUED', 'FAILED')
            """)
    int markQueued(@Param("ownerUserId") UUID ownerUserId, @Param("jobId") UUID jobId);

    /**
     * 抢占任务处理权，将状态更新为解析中。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param jobId 任务 ID
     * @return 更新行数
     */
    @Update("""
            update public.document_ingestion_job
            set status = 'PARSING', progress = 10, started_at = coalesce(started_at, now()), updated_at = now()
            where owner_user_id = #{ownerUserId}
              and id = #{jobId}
              and status in ('PENDING', 'QUEUED', 'FAILED')
            """)
    int claimForProcessing(@Param("ownerUserId") UUID ownerUserId, @Param("jobId") UUID jobId);

    /**
     * 更新任务运行阶段和进度。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param jobId 任务 ID
     * @param status 当前阶段状态
     * @param progress 进度百分比
     * @return 更新行数
     */
    @Update("""
            update public.document_ingestion_job
            set status = #{status}, progress = #{progress}, started_at = coalesce(started_at, now()), updated_at = now()
            where owner_user_id = #{ownerUserId}
              and id = #{jobId}
              and status <> 'INDEXED'
            """)
    int markRunningStage(@Param("ownerUserId") UUID ownerUserId,
                         @Param("jobId") UUID jobId,
                         @Param("status") String status,
                         @Param("progress") int progress);

    /**
     * 标记任务索引完成。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param jobId 任务 ID
     * @return 更新行数
     */
    @Update("""
            update public.document_ingestion_job
            set status = 'INDEXED', progress = 100, error_message = null, updated_at = now(), finished_at = now()
            where owner_user_id = #{ownerUserId}
              and id = #{jobId}
            """)
    int markIndexed(@Param("ownerUserId") UUID ownerUserId, @Param("jobId") UUID jobId);

    /**
     * 标记任务处理失败并记录错误信息。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param jobId 任务 ID
     * @param errorMessage 错误信息
     * @return 更新行数
     */
    @Update("""
            update public.document_ingestion_job
            set status = 'FAILED', error_message = #{errorMessage,jdbcType=LONGVARCHAR}, updated_at = now(), finished_at = now()
            where owner_user_id = #{ownerUserId}
              and id = #{jobId}
            """)
    int markFailed(@Param("ownerUserId") UUID ownerUserId,
                   @Param("jobId") UUID jobId,
                   @Param("errorMessage") String errorMessage);

    /**
     * 增加任务重试计数。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param jobId 任务 ID
     * @return 更新行数
     */
    @Update("""
            update public.document_ingestion_job
            set retry_count = retry_count + 1, updated_at = now()
            where owner_user_id = #{ownerUserId}
              and id = #{jobId}
            """)
    int incrementRetry(@Param("ownerUserId") UUID ownerUserId, @Param("jobId") UUID jobId);
}