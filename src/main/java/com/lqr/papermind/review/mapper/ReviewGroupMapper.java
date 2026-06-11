package com.lqr.papermind.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lqr.papermind.review.entity.ReviewGroupEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

public interface ReviewGroupMapper extends BaseMapper<ReviewGroupEntity> {

    /**
     * 查询指定组长名下的有效评审组
     *
     * @param leaderUserId 组长用户ID
     * @return 有效评审组列表
     */
    @Select("""
            select *
            from public.review_group
            where leader_user_id = #{leaderUserId}
              and status = 'ACTIVE'
            order by updated_at desc, created_at desc
            """)
    List<ReviewGroupEntity> selectActiveByLeader(@Param("leaderUserId") UUID leaderUserId);

    /**
     * 根据评审批次ID查询所有评审组
     *
     * @param batchId 评审批次ID
     * @return 评审组列表
     */
    @Select("""
            select *
            from public.review_group
            where batch_id = #{batchId}
            order by updated_at desc, created_at desc
            """)
    List<ReviewGroupEntity> selectByBatchId(@Param("batchId") UUID batchId);

    /**
     * 统计指定评审组下的评审任务数量
     *
     * @param groupId 评审组ID
     * @return 评审任务数量
     */
    @Select("""
            select count(*)
            from public.review_task
            where group_id = #{groupId}
            """)
    long countTasksByGroupId(@Param("groupId") UUID groupId);
}
