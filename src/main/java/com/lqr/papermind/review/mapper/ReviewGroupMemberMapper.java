package com.lqr.papermind.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lqr.papermind.review.entity.ReviewGroupMemberEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.UUID;

public interface ReviewGroupMemberMapper extends BaseMapper<ReviewGroupMemberEntity> {

    /**
     * 查询指定评审组的有效成员，组长排在最前
     *
     * @param groupId 评审组ID
     * @return 有效成员列表
     */
    @Select("""
            select *
            from public.review_group_member
            where group_id = #{groupId}
              and status = 'ACTIVE'
            order by case when member_role = 'LEADER' then 0 else 1 end, joined_at asc, created_at asc
            """)
    List<ReviewGroupMemberEntity> selectActiveByGroupId(@Param("groupId") UUID groupId);

    /**
     * 查询指定评审组和用户的有效成员记录
     *
     * @param groupId 评审组ID
     * @param userId 用户ID
     * @return 有效成员记录，不存在则返回null
     */
    @Select("""
            select *
            from public.review_group_member
            where group_id = #{groupId}
              and user_id = #{userId}
              and status = 'ACTIVE'
            limit 1
            """)
    ReviewGroupMemberEntity selectActiveByGroupAndUser(@Param("groupId") UUID groupId, @Param("userId") UUID userId);

    /**
     * 统计指定评审组的有效成员数量
     *
     * @param groupId 评审组ID
     * @return 有效成员数量
     */
    @Select("""
            select count(*)
            from public.review_group_member
            where group_id = #{groupId}
              and status = 'ACTIVE'
            """)
    long countActiveByGroupId(@Param("groupId") UUID groupId);

    /**
     * 将指定评审组的所有有效成员标记为已移除
     *
     * @param groupId 评审组ID
     * @return 更新的记录数
     */
    @Update("""
            update public.review_group_member
            set status = 'REMOVED', removed_at = now(), updated_at = now()
            where group_id = #{groupId}
              and status = 'ACTIVE'
            """)
    int deactivateByGroupId(@Param("groupId") UUID groupId);
}
