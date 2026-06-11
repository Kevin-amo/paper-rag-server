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

    /**
     * 根据评审任务ID查询所有分配记录
     *
     * @param taskId 评审任务ID
     * @return 分配记录列表
     */
    @Select("""
            select *
            from public.review_assignment
            where task_id = #{taskId}
            order by assigned_at asc, created_at asc
            """)
    List<ReviewAssignmentEntity> selectByTaskId(@Param("taskId") UUID taskId);

    /**
     * 查询指定任务和审核人的有效分配记录（最近一条）
     *
     * @param taskId 评审任务ID
     * @param reviewerUserId 审核人用户ID
     * @return 有效分配记录，不存在则返回null
     */
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

    /**
     * 查询指定任务的组长分配记录
     *
     * @param taskId 评审任务ID
     * @return 组长分配记录，不存在则返回null
     */
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

    /**
     * 统计指定任务的有效分配数量（排除已取消）
     *
     * @param taskId 评审任务ID
     * @return 有效分配数量
     */
    @Select("""
            select count(*)
            from public.review_assignment
            where task_id = #{taskId}
              and status <> 'CANCELLED'
            """)
    long countActiveByTaskId(@Param("taskId") UUID taskId);

    /**
     * 统计指定任务已提交的分配数量
     *
     * @param taskId 评审任务ID
     * @return 已提交的分配数量
     */
    @Select("""
            select count(*)
            from public.review_assignment
            where task_id = #{taskId}
              and status = 'SUBMITTED'
            """)
    long countSubmittedByTaskId(@Param("taskId") UUID taskId);

    /**
     * 更新分配记录状态
     *
     * @param id 分配记录ID
     * @param status 新状态
     * @return 更新的记录数
     */
    @Update("""
            update public.review_assignment
            set status = #{status},
                submitted_at = case when #{status} = 'SUBMITTED' then now() else submitted_at end,
                updated_at = now()
            where id = #{id}
            """)
    int updateStatus(@Param("id") UUID id, @Param("status") String status);

    /**
     * 查询指定任务的最大截止时间
     *
     * @param taskId 评审任务ID
     * @return 最大截止时间，无有效记录则返回null
     */
    @Select("""
            select max(due_at)
            from public.review_assignment
            where task_id = #{taskId}
              and status <> 'CANCELLED'
            """)
    OffsetDateTime maxDueAtByTaskId(@Param("taskId") UUID taskId);

    /**
     * 统计指定审核人和状态的分配记录数量
     *
     * @param reviewerUserId 审核人用户ID
     * @param status 分配状态
     * @return 分配记录数量
     */
    @Select("""
            select count(*)
            from public.review_assignment
            where reviewer_user_id = #{reviewerUserId}
              and status = #{status}
            """)
    long countByReviewerAndStatus(@Param("reviewerUserId") UUID reviewerUserId, @Param("status") String status);

    /**
     * 查询所有已分配审核人的用户ID（去重，排除已取消）
     *
     * @return 审核人用户ID列表
     */
    @Select("""
            select distinct reviewer_user_id
            from public.review_assignment
            where status <> 'CANCELLED'
            order by reviewer_user_id
            """)
    List<UUID> selectAssignedReviewerIds();
}
