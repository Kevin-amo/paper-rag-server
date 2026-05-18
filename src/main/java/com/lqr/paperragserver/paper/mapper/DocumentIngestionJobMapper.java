package com.lqr.paperragserver.paper.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lqr.paperragserver.paper.entity.DocumentIngestionJob;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.UUID;

public interface DocumentIngestionJobMapper extends BaseMapper<DocumentIngestionJob> {

    @Update("""
            update public.document_ingestion_job
            set status = 'QUEUED', progress = 5, error_message = null, updated_at = now()
            where owner_user_id = #{ownerUserId}
              and id = #{jobId}
              and status in ('PENDING', 'QUEUED', 'FAILED')
            """)
    int markQueued(@Param("ownerUserId") UUID ownerUserId, @Param("jobId") UUID jobId);

    @Update("""
            update public.document_ingestion_job
            set status = 'PARSING', progress = 10, started_at = coalesce(started_at, now()), updated_at = now()
            where owner_user_id = #{ownerUserId}
              and id = #{jobId}
              and status in ('PENDING', 'QUEUED', 'FAILED')
            """)
    int claimForProcessing(@Param("ownerUserId") UUID ownerUserId, @Param("jobId") UUID jobId);

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

    @Update("""
            update public.document_ingestion_job
            set status = 'INDEXED', progress = 100, error_message = null, updated_at = now(), finished_at = now()
            where owner_user_id = #{ownerUserId}
              and id = #{jobId}
            """)
    int markIndexed(@Param("ownerUserId") UUID ownerUserId, @Param("jobId") UUID jobId);

    @Update("""
            update public.document_ingestion_job
            set status = 'FAILED', error_message = #{errorMessage,jdbcType=LONGVARCHAR}, updated_at = now(), finished_at = now()
            where owner_user_id = #{ownerUserId}
              and id = #{jobId}
            """)
    int markFailed(@Param("ownerUserId") UUID ownerUserId,
                   @Param("jobId") UUID jobId,
                   @Param("errorMessage") String errorMessage);

    @Update("""
            update public.document_ingestion_job
            set retry_count = retry_count + 1, updated_at = now()
            where owner_user_id = #{ownerUserId}
              and id = #{jobId}
            """)
    int incrementRetry(@Param("ownerUserId") UUID ownerUserId, @Param("jobId") UUID jobId);
}