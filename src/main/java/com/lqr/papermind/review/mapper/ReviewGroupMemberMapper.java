package com.lqr.papermind.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lqr.papermind.review.entity.ReviewGroupMemberEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.UUID;

public interface ReviewGroupMemberMapper extends BaseMapper<ReviewGroupMemberEntity> {

    @Select("""
            select *
            from public.review_group_member
            where group_id = #{groupId}
              and status = 'ACTIVE'
            order by case when member_role = 'LEADER' then 0 else 1 end, joined_at asc, created_at asc
            """)
    List<ReviewGroupMemberEntity> selectActiveByGroupId(@Param("groupId") UUID groupId);

    @Select("""
            select *
            from public.review_group_member
            where group_id = #{groupId}
              and user_id = #{userId}
              and status = 'ACTIVE'
            limit 1
            """)
    ReviewGroupMemberEntity selectActiveByGroupAndUser(@Param("groupId") UUID groupId, @Param("userId") UUID userId);

    @Select("""
            select count(*)
            from public.review_group_member
            where group_id = #{groupId}
              and status = 'ACTIVE'
            """)
    long countActiveByGroupId(@Param("groupId") UUID groupId);

    @Update("""
            update public.review_group_member
            set status = 'REMOVED', removed_at = now(), updated_at = now()
            where group_id = #{groupId}
              and status = 'ACTIVE'
            """)
    int deactivateByGroupId(@Param("groupId") UUID groupId);
}