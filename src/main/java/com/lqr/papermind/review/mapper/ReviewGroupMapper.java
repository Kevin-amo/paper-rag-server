package com.lqr.papermind.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lqr.papermind.review.entity.ReviewGroupEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

public interface ReviewGroupMapper extends BaseMapper<ReviewGroupEntity> {

    @Select("""
            select *
            from public.review_group
            where leader_user_id = #{leaderUserId}
              and status = 'ACTIVE'
            order by updated_at desc, created_at desc
            """)
    List<ReviewGroupEntity> selectActiveByLeader(@Param("leaderUserId") UUID leaderUserId);

    @Select("""
            select *
            from public.review_group
            where batch_id = #{batchId}
            order by updated_at desc, created_at desc
            """)
    List<ReviewGroupEntity> selectByBatchId(@Param("batchId") UUID batchId);

    @Select("""
            select count(*)
            from public.review_task
            where group_id = #{groupId}
            """)
    long countTasksByGroupId(@Param("groupId") UUID groupId);
}