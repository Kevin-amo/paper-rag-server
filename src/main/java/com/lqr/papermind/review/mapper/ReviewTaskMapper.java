package com.lqr.papermind.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lqr.papermind.review.entity.ReviewTaskEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;
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
            select d.id, d.owner_user_id, d.source_id, d.title, 'PENDING_ASSIGNMENT'
            from public.paper_document d
            where d.deleted_at is null
              and d.status = 'INDEXED'
              and upper(coalesce(d.metadata ->> 'sourceType', '')) = 'REVIEW'
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
                reviewer_user_id = coalesce(cast(#{reviewerUserId,typeHandler=com.lqr.papermind.common.typehandler.UuidTypeHandler} as uuid), reviewer_user_id),
                assigned_at = case when cast(#{reviewerUserId,typeHandler=com.lqr.papermind.common.typehandler.UuidTypeHandler} as uuid) is null then assigned_at else coalesce(assigned_at, now()) end,
                completed_at = case when #{status} = 'COMPLETED' then now() else completed_at end,
                updated_at = now()
            where id = #{id,typeHandler=com.lqr.papermind.common.typehandler.UuidTypeHandler}
            """)
    int updateStatus(@Param("id") UUID id, @Param("reviewerUserId") UUID reviewerUserId, @Param("status") String status);

    @Update("""
            update public.review_task
            set status = #{status},
                completed_at = case when #{status} = 'COMPLETED' then now() else completed_at end,
                updated_at = now()
            where id = #{id}
            """)
    int updateTaskStatus(@Param("id") UUID id, @Param("status") String status);

    @Update("""
            update public.review_task
            set status = 'ASSIGNED',
                group_id = #{groupId},
                reviewer_user_id = #{reviewerUserId},
                assigned_by_user_id = #{assignedByUserId},
                leader_user_id = #{leaderUserId},
                assigned_at = coalesce(assigned_at, now()),
                due_at = #{dueAt},
                updated_at = now()
            where id = #{id}
            """)
    int markAssignedByLeader(@Param("id") UUID id,
                             @Param("groupId") UUID groupId,
                             @Param("assignedByUserId") UUID assignedByUserId,
                             @Param("leaderUserId") UUID leaderUserId,
                             @Param("reviewerUserId") UUID reviewerUserId,
                             @Param("dueAt") OffsetDateTime dueAt);

    @Select("""
            <script>
            select *
            from public.review_task
            where 1 = 1
            <if test="keyword != null and keyword != ''">
              and (
                source_id ilike concat('%', #{keyword}, '%')
                or title ilike concat('%', #{keyword}, '%')
              )
            </if>
            <if test="status != null and status != ''">
              and status = #{status}
            </if>
            order by updated_at desc, created_at desc
            </script>
            """)
    List<ReviewTaskEntity> selectAdminTasks(@Param("keyword") String keyword, @Param("status") String status);

    @Select("""
            <script>
            select t.*
            from public.review_task t
            where (
                exists (
                    select 1
                    from public.review_assignment a
                    where a.task_id = t.id
                      and a.reviewer_user_id = #{reviewerUserId}
                      and a.status &lt;&gt; 'CANCELLED'
                <if test="status != null and status != ''">
                      and a.status = #{status}
                </if>
                )
                or (
                    t.submitter_user_id = #{reviewerUserId}
                    and t.status = 'PENDING_ASSIGNMENT'
                    and not exists (
                        select 1
                        from public.review_assignment a
                        where a.task_id = t.id
                          and a.status &lt;&gt; 'CANCELLED'
                    )
                <if test="status != null and status != ''">
                    and #{status} = 'ASSIGNED'
                </if>
                )
            )
            <if test="keyword != null and keyword != ''">
              and (
                t.source_id ilike concat('%', #{keyword}, '%')
                or t.title ilike concat('%', #{keyword}, '%')
              )
            </if>
            order by t.updated_at desc, t.created_at desc
            </script>
            """)
    List<ReviewTaskEntity> selectReviewerTasks(@Param("reviewerUserId") UUID reviewerUserId,
                                               @Param("keyword") String keyword,
                                               @Param("status") String status);

    @Select("""
            select t.*
            from public.review_task t
            where t.group_id = #{groupId}
            order by t.updated_at desc, t.created_at desc
            """)
    List<ReviewTaskEntity> selectByGroupId(@Param("groupId") UUID groupId);

    @Select("""
            select t.*
            from public.review_task t
            where t.group_id = #{groupId}
              and t.status in ('PENDING_ASSIGNMENT', 'PENDING')
              and not exists (
                  select 1
                  from public.review_assignment a
                  where a.task_id = t.id
                    and a.status <> 'CANCELLED'
              )
            order by t.updated_at desc, t.created_at desc
            """)
    List<ReviewTaskEntity> selectUnassignedByGroupId(@Param("groupId") UUID groupId);
}
