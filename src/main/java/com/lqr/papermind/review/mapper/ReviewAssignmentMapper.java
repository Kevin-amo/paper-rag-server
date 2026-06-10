package com.lqr.papermind.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lqr.papermind.review.entity.ReviewAssignmentEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface ReviewAssignmentMapper extends BaseMapper<ReviewAssignmentEntity> {

    @Select("""
            select *
            from public.review_assignment
            where task_id = #{taskId}
            order by assigned_at asc, created_at asc
            """)
    List<ReviewAssignmentEntity> selectByTaskId(@Param("taskId") UUID taskId);

    @Select("""
            select *
            from public.review_assignment
            where task_id = #{taskId}
              and reviewer_user_id = #{reviewerUserId}
              and status <> 'CANCELLED'
            order by assigned_at desc, created_at desc
            limit 1
            """)
    ReviewAssignmentEntity selectActiveByTaskAndReviewer(@Param("taskId") UUID taskId,
                                                    @Param("reviewerUserId") UUID reviewerUserId);

    @Select("""
            select *
            from public.review_assignment
            where task_id = #{taskId}
              and role = 'LEAD'
              and status <> 'CANCELLED'
            order by assigned_at asc, created_at asc
            limit 1
            """)
    ReviewAssignmentEntity selectLeadByTaskId(@Param("taskId") UUID taskId);

    @Select("""
            select count(*)
            from public.review_assignment
            where task_id = #{taskId}
              and status <> 'CANCELLED'
            """)
    long countActiveByTaskId(@Param("taskId") UUID taskId);

    @Select("""
            select count(*)
            from public.review_assignment
            where task_id = #{taskId}
              and status = 'SUBMITTED'
            """)
    long countSubmittedByTaskId(@Param("taskId") UUID taskId);

    @Update("""
            update public.review_assignment
            set status = #{status},
                submitted_at = case when #{status} = 'SUBMITTED' then now() else submitted_at end,
                updated_at = now()
            where id = #{id}
            """)
    int updateStatus(@Param("id") UUID id, @Param("status") String status);

    @Select("""
            select max(due_at)
            from public.review_assignment
            where task_id = #{taskId}
              and status <> 'CANCELLED'
            """)
    OffsetDateTime maxDueAtByTaskId(@Param("taskId") UUID taskId);

    @Select("""
            select count(*)
            from public.review_assignment
            where reviewer_user_id = #{reviewerUserId}
              and status = #{status}
            """)
    long countByReviewerAndStatus(@Param("reviewerUserId") UUID reviewerUserId, @Param("status") String status);

    @Select("""
            select distinct reviewer_user_id
            from public.review_assignment
            where status <> 'CANCELLED'
            order by reviewer_user_id
            """)
    List<UUID> selectAssignedReviewerIds();
}
