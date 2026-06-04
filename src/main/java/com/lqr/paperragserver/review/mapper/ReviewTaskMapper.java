package com.lqr.paperragserver.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lqr.paperragserver.review.entity.ReviewTaskEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.UUID;

public interface ReviewTaskMapper extends BaseMapper<ReviewTaskEntity> {

    @Select("""
            select exists(
                select 1
                from public.review_task
                where document_id = #{documentId}
            )
            """)
    boolean existsByDocumentId(@Param("documentId") UUID documentId);

    @Update("""
            insert into public.review_task (document_id, submitter_user_id, source_id, title, status)
            select d.id, d.owner_user_id, d.source_id, d.title, 'PENDING'
            from public.paper_document d
            where d.deleted_at is null
              and d.status <> 'DELETED'
              and d.metadata ->> 'sourceType' = 'REVIEW'
              and not exists (
                  select 1 from public.review_task t where t.document_id = d.id
              )
            """)
    int syncFromDocuments();

    @Select("""
            select *
            from public.review_task
            where id = #{id}
            """)
    ReviewTaskEntity selectByIdIncludingDeleted(@Param("id") UUID id);

    @Update("""
            update public.review_task
            set status = #{status},
                reviewer_user_id = coalesce(cast(#{reviewerUserId,typeHandler=com.lqr.paperragserver.common.typehandler.UuidTypeHandler} as uuid), reviewer_user_id),
                assigned_at = case when cast(#{reviewerUserId,typeHandler=com.lqr.paperragserver.common.typehandler.UuidTypeHandler} as uuid) is null then assigned_at else coalesce(assigned_at, now()) end,
                completed_at = case when #{status} = 'COMPLETED' then now() else completed_at end,
                updated_at = now()
            where id = #{id,typeHandler=com.lqr.paperragserver.common.typehandler.UuidTypeHandler}
            """)
    int updateStatus(@Param("id") UUID id, @Param("reviewerUserId") UUID reviewerUserId, @Param("status") String status);
}