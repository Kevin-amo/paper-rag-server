package com.lqr.paperragserver.auth.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

/**
 * 用户角色关联 Mapper，负责维护用户与角色之间的绑定关系。
 */
public interface SysUserRoleMapper {

    /**
     * 插入用户角色关联，若已存在则忽略。
     *
     * @param userId 用户ID
     * @param roleId 角色ID
     * @return 影响行数
     */
    @Insert("""
            insert into public.sys_user_role (user_id, role_id)
            values (#{userId}, #{roleId})
            on conflict (user_id, role_id) do nothing
            """)
    int insertIgnore(@Param("userId") UUID userId, @Param("roleId") UUID roleId);

    /**
     * 删除指定用户的所有角色关联。
     *
     * @param userId 用户ID
     * @return 影响行数
     */
    @Delete("""
            delete from public.sys_user_role
            where user_id = #{userId}
            """)
    int deleteByUserId(@Param("userId") UUID userId);
}